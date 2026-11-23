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

**Status:** step 0 passed (2026-11-23) with the review fallback below; steps 1 and 2 have their tooling
(`tools/page-model`), step 3 is `:libs:pagesong`, step 4 crops every paste, drop, and share that is one text, with
**Use the whole page** in the editor, and reads the title and artist lines heading the song as its header, steps 5 and
6 read a page shared or pasted by its address on Android and iOS and take Safari's own page text, and step 8 crops
every PDF and Word import's songs; step 7 (optional) and step 9 are left. Written against the code on 2026-11-22
(67d82f42a). `convert/` is `libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/`.

A user looking at a song with chords on any website shares it to Campfire (or pastes it, or presses an optional browser
extension button), and the editor opens on that song with the page's clutter gone: menus, ads, cookie banners,
comments, "related songs", and the site's calls to action. Today a share that is only a link (Ultimate Guitar, the
most used site) is a dead end, and a share or paste that carries the page brings its clutter along, which the converter
deliberately does not filter (`convert/CLAUDE.md`: "Ultimate Guitar's page furniture … is deliberately not filtered").
The same model then serves documents: a website printed to PDF carries the same clutter, and a PDF's or Word file's
title, artist, key, and capo are misread where they stand among the page's other words.

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
  the song starts and ends on a page, which lines inside it are clutter, which line is the title and which the
  artist, and which lines hold the song's details. Labels: `SONG`, `TITLE`, `ARTIST`, `METADATA` (key, capo, tempo,
  time, credits), `JUNK`. A `METADATA` line is still parsed by the converter's own rules; the model only says where
  the details are, never what they say.
- **One model for pages and documents.** The task is the same, and a website printed to PDF is both. A document's
  lines bring what a page's text cannot (font size relative to the body, bold or italic, indent, column, a page
  break, the line's place on its page), all already measured for the converter (`ChordSheet.Span`, `ChordSheetLook`,
  `ChordSheetProfile`); for web text they are zero, and one input says which kind of source the lines came from.
  Splitting a document into songs stays `ChordSheetSongSplit`'s; the model crops each song it gives. Two models only
  if step 0 shows the shared one losing to one trained on either kind alone.
- **Small and pure Kotlin.** A character-level line encoder (hashed code points, so every script the app supports needs
  no vocabulary) and a small bidirectional sequence layer across lines, with a few of the converter's own line features
  (is a chord row, is a tab staff, is a heading) and the layout inputs as extra inputs. About a million parameters at
  most, int8 weights, a few MB. Inference is a handful of matrix multiplications written in `commonMain`: the same on Android, iOS, desktop,
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

1. **Lines in.** From a page: lines of text as rendered (`innerText`, or a paste's plain text, or `ChordSheetHtml`'s
   reading of a paste's HTML), plus the page title and address when known. From a PDF or Word file: each song's
   placed lines as `ChordSheetSongSplit` cut them, with their layout.
2. **Crop.** `:libs:pagesong` labels each line; the `SONG` and `METADATA` lines are kept in order, `TITLE` and
   `ARTIST` (checked against the page title or the document's own) become the header.
3. **Convert.** The kept lines go to `ChordSheetConverter` as today, and the editor opens as for any pasted new song
   (`presentation`'s rule: nothing written before Save), or the import review lists the document's songs.

The crop applies to web-sourced input (a share, a paste, a drop of text or HTML, a loaded link, an extension) from
step 4, and to PDF and Word files only from step 8, once `ImportCorpusTest` shows no document scoring worse with it.
A plain text file is left alone. Where the model is not confident the lines hold a song, nothing is cropped and
today's behaviour stands. The crop keeps every line the model gives even a small chance (2%) of being the song or its
details, and a cropped import always offers **Use the whole page**, which converts every line as today: the gate showed
the crop losing about 1–2% of song lines on sites it never saw, so the user can always get them back.

## Steps

### 0. Feasibility gate (before any app code)

In `tools/page-model/` (Python, pinned `requirements.txt`, following `tools/fonts`): save about 300 real song pages
from a varied set of sites and languages, label them with the teacher (step 1), train a first model (step 2), and
compare its crops against the converter alone on the same pages. Do the same for the import corpus's PDFs, labelled as
step 1 says, and train three variants (pages only, documents only, both) to settle whether one model serves both. Go
on only if the crop clearly wins on most pages and never loses a song's lines on a page where the converter alone kept
them. If it does not, stop here; nothing in the app has changed. If only the documents fail the gate, go on with pages
and drop step 8.

**Result (2026-11-23).** 616 labelled pages (two thirds English, about 250 sites, 20 languages) and 939 documents (87
import corpus files, 483 pages printed to PDF, 369 made up), five folds by song, two seeds. One model trained on both
beats either trained alone, on pages and on documents. With the 2% crop it beats keeping every line on 427–431 of 473
song pages (worse on 4–6; line F1 0.53 → 0.88) and on 377–396 of 456 song documents (worse on 22–28; 0.70 → 0.89–0.92),
and tells about 94% of the pages with no song. It still loses about 1–2% of song lines, mostly on kinds of page it has
not seen (lyrics with no chords, chords glued into words), and each new batch of sites brings new ones, so more data
alone will not reach zero. The gate is passed on that basis, with the "Use the whole page" fallback as the condition.

### 1. The corpus and its labels (`tools/page-model/`)

- **Collector**: given a list of addresses, renders each page in headless Chrome (Playwright) and saves its `innerText`
  lines, title, and address as one JSON file into the private folder named by a new `campfire.pageCorpus` property
  (in `local.properties` only). A few thousand pages across the big chord sites, smaller ones, blogs, forums, worship
  sites, and every app language, tabs and ABC pages included, plus pages with no song (the model must learn to say so).
- **Teacher**: Claude labels each page's lines offline (subagents in a Claude Code session, following
  `teacher/INSTRUCTIONS.md`): line numbers and labels only, never text. Two independent runs per page; pages where the
  runs disagree are dropped rather than checked by hand.
- **Documents, labelled for free**: the import corpus already pairs each PDF and Word file with its corrected `.cho`.
  Aligning the two labels each line: a line found in the song is `SONG`, the line holding the `{title}` or `{artist}`
  value is `TITLE` or `ARTIST`, one holding `{key}`, `{capo}`, `{tempo}`, `{time}`, or a credit is `METADATA`, and the
  rest is `JUNK`. A document whose lines do not align well enough is left out rather than fixed by hand.
- **Websites printed to PDF**: the collector also saves each page as a PDF (Playwright's `page.pdf`, the way a browser
  prints), read back through the app's own PDF reader from a small Gradle task; the page's teacher labels carry over by
  aligning its lines. Each app language's paper size and a print stylesheet come with the page, as a user's print would.
- **Synthetic pages**: ChordPro songs (the private import corpus's corrected `.cho`s) rendered as chord sheets and
  wrapped in clutter sampled from the real pages' `JUNK` lines, so the labels are known by construction. They widen
  the training set; the score is always taken on real pages.
- A fixed tenth of the real pages and of the documents is held out as the test set and never trained on.

### 2. The model and its training (`tools/page-model/`)

- PyTorch, trained on a Mac in minutes to hours. Line encoder: hashed code points (4,096 buckets) → embedding → a few
  1-D convolutions → max-pool; per-line features computed by the same rules the Kotlin side will compute (step 3 pins
  them to one definition), the layout inputs, and the source flag; a two-layer bidirectional GRU across the lines;
  one softmax per line.
- Export: int8 weights with per-row scales into one binary file, plus a golden file of a few pages' inputs and the
  logits PyTorch produced for them.
- The trainer prints the held-out score (line accuracy, song-span precision and recall, title and artist hits) and
  writes it next to the weights, for pages and documents apart, so a retrain is judged against the last on both.

### 3. `:libs:pagesong` (pure Kotlin, `explicitApi()`)

- `PageSong.crop(lines, source, title): Crop?`: each line's text and optional layout, the kind of source, and the
  page's or document's own title; it answers the kept line indices, the title and artist, the metadata lines, and
  whether the lines hold a song at all. The library's line type is its own; `:libs:chordsheet` maps its placed lines
  onto it, so the dependency runs from `chordsheet` to `pagesong`. Weights are passed in as a `ByteArray` by the caller, so the library holds no resources and depends on
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
  of each page's crop and song, never in CI. `ImportCorpusTest` must not move, since documents do not pass the crop
  until step 8.

**Done (2026-11-23).** The seam is `PageCropper` in `:domain:api`, which `:presentation` implements
(`PageCropperImpl`: `Res.readBytes("files/page-model.bin")` once, on the first page, off the main thread, the page as
`PageLineSource.PAGE` lines with no layout, the title and artist lines kept with the song) and `PrepareImportUseCaseImpl`
asks for an `ImportedFile.isPageText` text (`PageTextCrop`). Only the paste, drop, or share that is one text is marked;
several shared texts, a `.txt` picked or opened, ChordPro, ABC, and documents are read whole. No song, a failure, or no
weights keep every line. A cropped song carries the whole page's conversion (`ImportPlan.SongEntry.wholePageText`), which
the editor's banner offers as **Use the whole page** (`WholePageOffer`, `WholePageBanner`) until it is used, dismissed,
or the text changes. `PageCorpusTest` (`data/source/local/implementation`) prints the gate's page-level numbers (song
lines lost, how often the crop beats keeping everything) for the shipped weights over the corpus's labelled pages, all
of which the shipped model was trained on, so only pages labelled after a training are held out; it asserts only loose
bounds.

**Done (2026-11-23): the header.** `PageCrop.headerLines` are the title and artist lines heading the song: before its
first `SONG` line, not running straight into it (a lyric read as a title most often opens the song), and not kept. The
import (`PageTextCrop`) converts the crop with them first, and where the converter reads no title of its own from it and
it is one song, converts it again without them, guessing no title; `:libs:chordsheet`'s `PageTitle` (the share subject's
rule, moved there) reads them into `SongEntry.pageTitle` / `pageArtist`, which the editor suggests and selects ahead of
the subject; Use the whole page keeps them in its text. On `PageCorpusTest` (all trained-on pages): song lines lost
unchanged (155 of 26,723 on 31 pages), the line F1 0.9292 → 0.9420 since header lines no longer count as kept clutter,
header lines read on 372 of 473 song pages and left to the converter's own reading on 42.

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

**Done (2026-11-23).** `PageSource` (`:data:source:remote:api`) says whether the platform can read a page at all
(`canLoadPages`) and answers `LoadedPage` (`:data:model`): `Read` (text, title, final address) or `NotRead` with its
reason: `UNREACHABLE` where the network failed the load (Android's host lookup, connect, timeout, and I/O errors,
`NSURLErrorDomain`'s no connection, host not found or not answering, lost connection, and timeout) or the page was
still loading with nothing readable at the time limit, else `UNREADABLE`. `OffscreenPageSource` is the common loop over an `OffscreenBrowser`: a look every 500 ms, taken once loaded and
unchanged for 1.5 s, else the last readable look at 20 s; a bot check (`PageSnapshot.isBotCheck`, short pages only) is
waited past and never taken. Android's `WebViewBrowser` refuses every navigation and subresource off a public `https`
host (`isPublicHttpsAddress`, the covers' rule); iOS's `WebKitBrowser` every navigation, in a non-persistent data store;
desktop and web are `NoPageSource`. Up through `PageRepository`, `LoadPageUseCase` (which asks the switch again) and
`CanLoadPagesUseCase`. In the app a page's address is `ImportSource.Page`: read in `ImportWork` as the import screen's
loading stage says "Reading the page…" (`ImportProgress.Phase.READING_PAGE`, the notification too), asked of the page
model before conversion, and then the pasted page it is; its song's editor replaces the loading screen. Unreachable:
the failure stage says Campfire could not reach the page and to check the connection (`ImportFailure.PAGE_UNREACHABLE`);
unreadable, or no song: open it in the browser and share or copy the page itself (`ImportFailure.PAGE_NOT_READ`); both
with Try again. A timeout is told by what the user can do: nothing drawn is a connection to try again, a bot check
that stays is the browser's sentence. A model that cannot run (its
weights unread) throws rather than answering no song (`PageCropper.songLinesOf`), so the page opens whole in the
editor, as a paste does, with no Use the whole page. **Pages from links**
(`isPageLinkLoadingEnabled`, on by default, `upgrade-5.5.0`) sits under Pictures from links, only where pages can be
read.

### 6. The share doors

- **Android**: a share that is only an address (or an address with a site's call to action around it) loads it
  through `PageSource`; a share with text goes straight to the crop.
- **iOS**: the share extension gains an `NSExtensionJavaScriptPreprocessingFile`, so a share from Safari hands over
  the page's own `innerText` and title, with nothing loaded again; a share from another browser that gives only the
  address is written to the inbox as an address and loaded by the app on its next open (the extension's memory limit
  keeps the webview out of it).
- **Desktop and web**: paste and drop already reach the pipeline; a pasted address alone gets the sentence from step 5.

**Done (2026-11-23).** Every door ends in `PastedSongController`, where `pageAddressIn` decides that a text is a page's
address: one address, alone or among at most three short lines (200 characters) of a call to action or a title,
trailing punctuation dropped, never two different addresses and never Campfire's own site (`sharedLinkIn` is asked
first). Android's share and every paste or drop take that path; a text with more in it goes to the crop as before.
iOS's extension runs `PageText.js` in Safari (`NSExtensionJavaScriptPreprocessingFile`) and writes its `innerText` as
the share's one text with the title as subject, nothing loaded again; a URL from any other app (one that is no file's,
now in the activation rule) is written as a text of its address, read by the app on its next open. The extension's
"does not open web pages" refusal and its strings are gone. Desktop and web answer an address with
`Message.PageLinkNotReadHere`.

### 7. Optional: browser extensions

One WebExtension for Chrome, Edge, and Firefox, and its Safari Web Extension build inside the iOS app: a toolbar
button that reads the page's text and title and hands them to Campfire. On iOS it hands over through the app's own
inbox, as a share does. On the desktop, how it reaches the running app (native messaging, or the single-instance
endpoint `instance.endpoint` already accepts) and the open web build tab is a spike of its own, settled before this
step starts. Each store's listing and review is the cost; nothing else depends on it.

### 8. Documents

- The document import (`ChordSheetConverter`'s entry for placed lines) passes each song `ChordSheetSongSplit` gives
  through the crop, with its layout, before the converter reads it; the `METADATA` lines the model marks are where the
  converter looks for the song's details, so a key or capo among the page's other words is found, and a running
  header, a page number, a site's footer, or a printed page's address and date is dropped.
- Judged by `ImportCorpusTest` as every converter change is (`convert/CLAUDE.md`): it lands only if no document's
  overall score falls, and the report's websites printed to PDF show the gain. Until then the crop stays off for
  documents and the rest of the plan stands without it.

**Done (2026-11-23).** `ChordSheetPageLines` (`:libs:chordsheet`, now depending on `:libs:pagesong`) is the one mapping
of a document's lines and layout, which `PageCorpusDocumentsDump` now writes the trainer's documents through (a re-dump
matched the trained-on inputs on all 155,882 lines). `PrepareImportUseCaseImpl` asks `PageCropper.documentScoresOf` for
the whole document's scores (`PageDocumentCrop`; the model reads the document whole, as in training), and
`ChordSheetConverter.convert`'s `pageScores` crops each song `ChordSheetSongSplit` gives by them (`ChordSheetSongCrop`,
`PageCrop.of`): the crop rule on the song's own lines, plus every line with a 2% chance of being the title or artist
(the best one alone lost a wrapped title and picked an album line over the title), the blank lines between kept ones,
and a chord row from the song's first song line on (a lone closing chord read as a page number). `ImportCorpusTest`
crops as the app does: against the unchanged code no document falls, 14 rise (overall 0.8422 → 0.8514), the four
tab4u pages printed to PDF most (+0.08 to +0.28); one, `kol_galgal_print`, gains overall but its header falls (a credit
now read as the artist). Every corpus document was in the model's training set, so these are not held-out numbers. The
import review offers no **Use the whole page**: a document's songs are not opened in the editor, and nothing in the
corpus asked for it.

### 9. Documentation

- `libs/pagesong/CLAUDE.md`: the labels, the inputs, the weights format, and how a retrain is judged.
- `tools/page-model/README` or `CLAUDE.md`: how to collect, label, train, and export.
- Root `CLAUDE.md`: the module in the architecture tree, the privacy entry, and the product rule on shared chord sheets.
- At release time: the website's import page, the store listings, the privacy policy, and what's new.

## Size and upkeep

The tool is a few Python scripts; the library is a few small Kotlin files and one weights file of a few MB; the app
changes are the crop's two calls (pages and documents), `PageSource` on two platforms, a switch, and the share doors.
Upkeep is a retrain when `PageCorpusTest` on a fresh batch of pages, or `ImportCorpusTest`, shows the score falling,
not code per site. The biggest effort is step 1; the biggest risk is step 0, which is why it comes first.
