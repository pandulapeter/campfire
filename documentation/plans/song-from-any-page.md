<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: a song from any page

**Status:** not started. Written against the code on 2026-11-22 (67d82f42a). `convert/` is
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/`.

A user looking at a song with chords on any website shares it to Campfire (or pastes it, or presses an optional browser
extension button), and the editor opens on that song with the page's clutter gone: menus, ads, cookie banners,
comments, "related songs", and the site's calls to action. Today a share that is only a link (Ultimate Guitar, the
most used site) is a dead end, and a share or paste that carries the page brings its clutter along, which the converter
deliberately does not filter (`convert/CLAUDE.md`: "Ultimate Guitar's page furniture … is deliberately not filtered").

## Decisions

- **No code knows any site.** Adapters per site break whenever a site changes, and testing hundreds of them is not
  maintainable by one person. Every page goes through one pipeline; a site's redesign is just another page to it.
- **A small model of our own decides which lines are the song**, trained for this one task and shipped with the app.
  Not a large language model (cloud ones need a server or the user's key; the ones the operating systems provide run on
  some devices only), and it never writes text: it labels the page's lines, and the lines it keeps reach the converter
  exactly as they were. So nothing can be made up, nothing is refused for being copyrighted, and chords keep their
  columns.
- **The model only crops; the converter still converts.** Chords, lyrics, sections, tabs, and metadata stay
  `ChordSheetConverter`'s job, already judged by the import corpus. The model answers what the converter cannot: where
  the song starts and ends on a page, which lines inside it are clutter, and which line is the title and which the
  artist. Labels: `SONG`, `TITLE`, `ARTIST`, `JUNK`.
- **Small and pure Kotlin.** A character-level line encoder (hashed code points, so every script the app supports needs
  no vocabulary) and a small bidirectional sequence layer across lines, with a few of the converter's own line features
  (is a chord row, is a tab staff, is a heading) as extra inputs. About a million parameters at most, int8 weights, a
  few MB. Inference is a handful of matrix multiplications written in `commonMain`: the same on Android, iOS, desktop,
  and the web build, no native runtime, no dependency to credit or keep updated.
- **Our own weights, trained on a private corpus.** The pages trained on are never committed or shipped (like
  `ImportCorpusTest`'s corpus), and a classifier this size cannot reproduce a song. The training tool is committed;
  its data is not.
- **A shared link is loaded on the device, hidden.** Android and iOS load it in an offscreen webview (a real browser
  engine, so the page renders as the user saw it) and read its text; the user sees a short "Reading the page…" state,
  never the page. This is a new kind of network request: a Features switch removes it, and the root `CLAUDE.md`'s
  privacy list names it. Desktop and web cannot load another site's page (no webview in the JVM, cross-origin rules in
  the browser); there a link alone says to copy the page itself, or to use the extension.
- **One entry, many doors.** Share (text, a link, or Safari's page), paste, drop, and the optional extensions all hand
  the same thing to the pipeline: the page's lines, its title, and its address. Nothing differs after that.
- **The browser extensions are optional shortcuts**, the last step, never needed for anything a share or a paste
  cannot do.

## The pipeline

1. **Page in.** Lines of text as rendered (`innerText`, or a paste's plain text, or `ChordSheetHtml`'s reading of a
   paste's HTML), plus the page title and address when known.
2. **Crop.** `:libs:pagesong` labels each line; the `SONG` lines are kept in order, `TITLE` and `ARTIST` (checked
   against the page title) become the header.
3. **Convert.** The kept lines go to `ChordSheetConverter` as a paste does today, and the editor opens as for any
   pasted new song (`presentation`'s rule: nothing written before Save).

The crop applies only to web-sourced input (a share, a paste, a drop of text or HTML, a loaded link, an extension),
never to a PDF, Word, or plain text file, which are documents rather than pages. Where the model is not confident the
page holds a song, nothing is cropped and today's behaviour stands.

## Steps

### 0. Feasibility gate (before any app code)

In `tools/page-model/` (Python, pinned `requirements.txt`, following `tools/fonts`): save about 300 real song pages
from a varied set of sites and languages, label them with the teacher (step 1), train a first model (step 2), and
compare its crops against the converter alone on the same pages. Go on only if the crop clearly wins on most pages and
never loses a song's lines on a page where the converter alone kept them. If it does not, stop here; nothing in the app
has changed.

### 1. The corpus and its labels (`tools/page-model/`)

- **Collector**: given a list of addresses, renders each page in headless Chrome (Playwright) and saves its `innerText`
  lines, title, and address as one JSON file into the private folder named by a new `campfire.pageCorpus` property
  (in `local.properties` only). A few thousand pages across the big chord sites, smaller ones, blogs, forums, worship
  sites, and every app language, tabs and ABC pages included, plus pages with no song (the model must learn to say so).
- **Teacher**: Claude labels each page's lines once, offline, through the API with the owner's key: line numbers and
  labels only, never text. Two runs per page; pages where the runs disagree are dropped rather than checked by hand.
- **Synthetic pages**: ChordPro songs (the private import corpus's corrected `.cho`s) rendered as chord sheets and
  wrapped in clutter sampled from the real pages' `JUNK` lines, so the labels are known by construction. They widen
  the training set; the score is always taken on real pages.
- A fixed tenth of the real pages is held out as the test set and never trained on.

### 2. The model and its training (`tools/page-model/`)

- PyTorch, trained on a Mac in minutes to hours. Line encoder: hashed code points (4,096 buckets) → embedding → a few
  1-D convolutions → max-pool; per-line features computed by the same rules the Kotlin side will compute (step 3 pins
  them to one definition); a two-layer bidirectional GRU across the page's lines; one softmax per line.
- Export: int8 weights with per-row scales into one binary file, plus a golden file of a few pages' inputs and the
  logits PyTorch produced for them.
- The trainer prints the held-out score (line accuracy, song-span precision and recall, title and artist hits) and
  writes it next to the weights, so a retrain is judged by one number against the last.

### 3. `:libs:pagesong` (pure Kotlin, `explicitApi()`)

- `PageSong.crop(lines, pageTitle): Crop?`: the kept line indices, the title and artist, and whether the page holds a
  song at all. Weights are passed in as a `ByteArray` by the caller, so the library holds no resources and depends on
  no other module.
- The forward pass in plain Kotlin arrays (embedding lookup, convolution, GRU, softmax), int8 dequantized per row.
- Tests in `commonTest`: the golden logits matched within a tolerance on every platform's run, an empty page, a page
  of one line, and a page past the length cap (pages are cut to a bounded number of lines, the song is rarely near the end).
- The line features the model reads are computed here, and the trainer's Python copy is checked against them through
  the golden file.

### 4. The crop in the import

- `PrepareImportUseCaseImpl` (`domain/implementation/.../importing/`) passes web-sourced text through the crop before
  conversion; the weights ship under `presentation`'s `composeResources/files` and are read once, lazily.
- A new `PageCorpusTest` beside `ImportCorpusTest` runs the whole pipeline over the held-out pages and writes a report
  of each page's crop and song, never in CI. `ImportCorpusTest` must not move, since documents never pass the crop.

### 5. Loading a shared link

- `PageSource` in `:data:source:remote:api`, implemented in `:data:source:remote:implementation` (still the only module
  that makes a network call): Android `WebView` and iOS `WKWebView`, offscreen, `https` only and public hosts only
  (the same rule as cover images), a time limit, then `document.body.innerText`, `document.title`, and the final
  address. Desktop and web have no implementation.
- A page that comes back as a bot challenge or with no song says so, with one sentence: open it in the browser and
  share or copy the page itself.
- A **"Pages from links"** switch in Settings → Features, on by default, beside "Pictures from links".
- The rule "never a link followed, except a Campfire share link" becomes "a shared link is read once on the device
  when the switch allows it", in the root `CLAUDE.md` and `presentation`'s.

### 6. The share doors

- **Android**: a share that is only an address (or an address with a site's call to action around it) loads it
  through `PageSource`; a share with text goes straight to the crop.
- **iOS**: the share extension gains an `NSExtensionJavaScriptPreprocessingFile`, so a share from Safari hands over
  the page's own `innerText` and title, with nothing loaded again; a share from another browser that gives only the
  address is written to the inbox as an address and loaded by the app on its next open (the extension's memory limit
  keeps the webview out of it).
- **Desktop and web**: paste and drop already reach the pipeline; a pasted address alone gets the sentence from step 5.

### 7. Optional: browser extensions

One WebExtension for Chrome, Edge, and Firefox, and its Safari Web Extension build inside the iOS app: a toolbar
button that reads the page's text and title and hands them to Campfire. On iOS it hands over through the app's own
inbox, as a share does. On the desktop, how it reaches the running app (native messaging, or the single-instance
endpoint `instance.endpoint` already accepts) and the open web build tab is a spike of its own, settled before this
step starts. Each store's listing and review is the cost; nothing else depends on it.

### 8. Documentation

- `libs/pagesong/CLAUDE.md`: the labels, the inputs, the weights format, and how a retrain is judged.
- `tools/page-model/README` or `CLAUDE.md`: how to collect, label, train, and export.
- Root `CLAUDE.md`: the module in the architecture tree, the privacy entry, and the product rule on shared chord sheets.
- At release time: the website's import page, the store listings, the privacy policy, and what's new.

## Size and upkeep

The tool is a few Python scripts; the library is a few small Kotlin files and one weights file of a few MB; the app
changes are the crop's call, `PageSource` on two platforms, a switch, and the share doors. Upkeep is a retrain when
`PageCorpusTest` on a fresh batch of pages shows the score falling, not code per site. The biggest effort is step 1;
the biggest risk is step 0, which is why it comes first.
