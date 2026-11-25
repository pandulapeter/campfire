<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: the page model sees the page's structure

**Status:** the experiment is done and every mark earns its place (third gate runs). Under way (2026-11-25): step 1's
two diagnostic gates (`.runs/diag-seed11/` and `.runs/diag-nostaves/`, both variant, `gate.py --out`, over the corpus
before step 2), then step 4's gates. Step 2 is done; steps 3, 5, and 6 are written in the session's worktree and land
together with the weights step 4 exports, since a change to `lines.py` ships with its port (*Next session*, at the end).
When the app reads a web page itself, it hands the page model each line's structure too: whether the line is a link or
a button, whether it starts a new card or section, and how it is set. The crop can then drop calls to action and
partner cards next to a song, which text alone cannot tell apart from the song's own notes.

## Why

`https://gitartab.hu/hu/tab/tankcsapda/ez-az-a-haz/293`, read as a link, keeps "A dalok kíséréséhez ajánljuk", "Adj erőt
a Tankcsapda-riffeknek!", the first "Megnézem a Thomannál →", and the capo hint above the chords. The shipped weights
already score those lines 79–95% `JUNK`, but the crop (`CropRule.kt`, `gate.py`'s `unsure`) keeps any line with a 2%
chance of `SONG` or `METADATA`, and that margin pays for itself: on the gate's held-out pages, every stricter rule
tried (5% or 20% outside the song's span, uncertain lines kept only between song lines) removed clutter only by losing
song lines at a steady rate. A retrain will not help either (the page is in the corpus, labelled, beside 29 from its
site). The model cannot see what the reader sees: a page line's layout inputs are zero, and `innerText` drops which
lines are links, buttons, or a separate card.

## Decisions

- **The text stays `innerText`, unchanged.** The structure comes as per-line marks beside it, one per line of
  `text.lines()`, so the text the converter, the editor, and **Use the whole page** see is exactly today's. A page
  whose marks are missing or do not match its line count is cropped as today, from its text alone.
- **Only where the app reads the page itself.** The link read (`PAGE_SNAPSHOT_SCRIPT`, run by Android's, iOS's, and the
  desktop's web views), Safari's share extension (`PageText.js`), and the corpus collector (`collect.py`) all run
  one shared script on the live page. Pasted, dropped, and shared plain text, documents, and the web build have no page
  to walk and keep zero marks; reading a clipboard's HTML is not part of this plan.
- **The marks**, kept only where the gate says they help:
  - *link*: every visible character of the line is inside a link, a button, or a form control (a `<select>`'s
    options included);
  - *new block*: the line's nearest card (`article`, `section`, `aside`, `nav`, `header`, `footer`, `form`, `li`, or a
    block with its own border, background, or shadow) differs from the previous printed line's;
  - *landmark*: the line is inside `nav`, `header`, `footer`, `aside`, or `form` (or their ARIA roles);
  - *how it is set*: the document layout inputs where the DOM says the same thing: `size` (its font size over the
    body's), `bold`, `italic`, `monospace` (`pre`, `code`, or a monospace font), and `heading` (`h1`–`h6`). `page`,
    `column`, and `indent` stay zero for a page.
- **Marks are found by aligning, not by rebuilding the text.** The script walks the visible text nodes in order and
  finds each node's words, one after another, in `innerText` near where the last node ended; a line takes the marks of
  most of its characters, and a line under 90% covered gets none. Measured over the corpus: 0.3% of printed lines
  unaligned (text drawn by CSS), 9 ms a page. The script runs in the page's own context, so it keeps to plain loops
  and `slice` (Prototype.js, still on some sites, breaks `Array.from` and `startsWith`); its comments say what else
  each guard is for.
- **A page without marks must not get worse.** Training sees every marked page a second time with its marks zeroed, and
  the gate scores pages both ways. A paste is that second case, so the new model must hold up against today's on
  unmarked pages.
- **The source flag stays two-valued.** A page with marks is still a page; its layout inputs are no longer zero, which
  `libs/pagesong/CLAUDE.md` and `lines.py`'s docstring then say.

## Where it stands

The page corpus's `marked/` (its `README.md` says what each file is) holds every page read again on 2026-11-24 with
the script (`marked/spike/page-marks.js`): `<id>.json` with today's `lines` and their `marks`, and `labels/`, the
agreed labels moved onto today's lines (unmatched lines `IGNORE`; the ten pages whose song had moved labelled again by
the teacher). `pages/` and `labels/` are untouched, so today's gate, export, and `PageCorpusTest` still run on them;
eleven pages could not be read again and have no marked copy.

How often each mark is set by label (`size` ≥ 1.2 as *big*), and on how many of the 118 sites with enough lines of
both it is set more often on clutter than on song lines (for `monospace`, the reverse):

| Label | link | new block | landmark | bold | italic | monospace | heading | big |
|---|---|---|---|---|---|---|---|---|
| `SONG` | 4.7% | 3.4% | 0.1% | 26.5% | 0.8% | 64.8% | 0.1% | 5.8% |
| `JUNK` | 56.6% | 55.7% | 27.8% | 23.9% | 3.4% | 3.9% | 6.3% | 6.9% |
| Sites it separates | 115 | 115 | 94 | 85 | 37 | 34 | 104 | |

*italic*, *bold*, and *monospace* (strong overall, but on few sites) are the likeliest to be dropped. On the
gitartab.hu page, the partner card is *landmark*, its buttons *link*, and the capo hint *new block*.

## Which pages count most

Ultimate Guitar first: most people who bring a song from the web bring it from there, so its pages are judged on their
own before anything else, and with page-grouped folds (the shipped model trains on its pages). Then the sites a search
for a song's chords puts in its first results, by how often they come up; then every other site, scored with
site-grouped folds. A change that helps the first two is not held back because unseen sites gain less, as long as
they do not lose.

## First gate runs

Pages only, the `unsure` crop, the 492 song pages every run scores; *paste* is the marks model given the same pages
without marks:

| Folds | Model | Mean line F1 | Pages losing a song line | Song lines lost | Clutter kept |
|---|---|---|---|---|---|
| page | today | 0.896 | 46 | 341 | 5,502 |
| page | marks | 0.925 | 36 | 173 | 3,372 |
| page | marks, paste | 0.913 | 44 | 213 | 4,397 |
| site | today | 0.857 | 59 | 425 | 8,055 |
| site | marks | 0.873 | 69 | 443 | 6,649 |
| site | marks, paste | 0.864 | 87 | 419 | 7,566 |

On Ultimate Guitar's 25 song pages no run loses a song line, and the marks take the clutter kept from 69 lines to 55
(36 site-grouped): its toolbar ("Download PDF", "Simplify", "Transpose") goes, its subscription offer ("Welcome
Offer", "80% Off", "Try now", a close button "X" that is not a button) stays. The corpus held only 26 of its pages,
all chord sheets, and none of Cifra Club's or e-chords' (the headless browser was turned away).

## Second gate runs and the corpus they led to

Site-grouped, with a second seed for today's model, whose runs differ by 7 pages losing a song line and 175 lines:

| Model | Seed | Mean line F1 | Pages losing a song line | Song lines lost | Clutter kept |
|---|---|---|---|---|---|
| today | 7 | 0.854 | 60 | 427 | 8,234 |
| today | 11 | 0.854 | 53 | 252 | 9,076 |
| every mark | 7 | 0.870 | 70 | 447 | 6,809 |
| every mark | 11 | 0.882 | 91 | 669 | 6,592 |
| link, new block, landmark | 7 | 0.878 | 69 | 397 | 7,101 |
| those, size, heading | 7 | 0.863 | 79 | 366 | 8,036 |

On a site never seen, every set of marks keeps less clutter but loses a song line on more pages: the marks ship only once
that gap closes, on a corpus with more sites. So the corpus grew on 2026-11-25:

- 60 more Ultimate Guitar pages (40 chord sheets, 20 tabs), from its most-viewed and trending lists.
- The sites a search for a song's chords and the chords word in each app language puts in its first five: 26 Google
  searches in local Google, 90 more through a US search engine. Ultimate Guitar leads everywhere but Spanish and
  Portuguese, where Cifra Club is first; GuitarTuna, Songsterr, Chordify, Hooktheory, PraiseCharts, e-chords, and
  local sites (tabulaturi.ro, gitarkotta.com, boiteachansons.net, pisnicky-akordy.cz, mychords.net) follow. 182 of their
  pages, Cifra Club's and e-chords' read in a visible browser where the headless one is turned away.
- Cifra Club, e-chords, and ukutabs.com set their song in blocks with `content-visibility: auto`, which `innerText`
  leaves out until they scroll into view: the app's link read and Safari's share read them without the song. Both now
  draw every element for the moment of the read, and so does `page-marks.js`; the corpus pages of the ten hosts that
  hide text this way were read again and labelled again.

## Third gate runs: the marks earn their place

Over the grown corpus (882 pages, 695 with a song), pages only, the `unsure` crop with the staff rule (`CropRule.kt`),
the 613 song pages every run scores. *Paste* is the marks model given the same pages without marks:

| Folds | Model | Mean line F1 | Pages losing a song line | Song lines lost | Clutter kept |
|---|---|---|---|---|---|
| page | today | 0.912 | 53 | 328 | 6,634 |
| page | every mark | 0.928 | 50 | 248 | 5,561 |
| page | every mark, paste | 0.920 | 68 | 596 | 6,133 |
| page | link, new block, landmark | 0.926 | 70 | 368 | 5,415 |
| site | today | 0.869 | 95 | 1,765 | 11,360 |
| site | every mark | 0.894 | 69 | 678 | 8,723 |
| site | every mark, paste | 0.875 | 70 | 640 | 11,185 |
| site | link, new block, landmark | 0.882 | 61 | 577 | 9,937 |

On Ultimate Guitar's 81 song pages (page-grouped), every mark takes the line F1 from 0.976 to 0.983, the song lines
lost from 79 to 44, and the clutter kept from 372 to 242; site-grouped, from 0.892 to 0.974. The gap the second runs
found has closed: on a site never seen, every mark now loses fewer song lines on fewer pages than today. **Every mark
ships.** The paste case's extra losses are half one page (lecarnetdenotes.com's *La Bohème*, 251 of 515 lines against
3), the rest even with today's.

## A plain retrain on the grown corpus (2026-11-25): not shipped

Today's recipe (no marks), with the staff rule and the staves `IGNORE` training below, gated on the grown corpus (887
pages, 767 documents; both variant, `.runs/gate-official2.txt` and `.tsv`) and compared with the shipped model's report
(`.runs/gate-shipped.txt` and `.tsv`) on the 466 song pages whose text and labels have not changed since:

| Pages | Model | Mean line F1 | Pages losing a song line | Song lines lost | Clutter kept |
|---|---|---|---|---|---|
| Ultimate Guitar (25) | shipped | 0.975 | 0 | 0 | 91 |
| Ultimate Guitar (25) | retrained | 0.980 | 0 | 0 | 81 |
| other sites (441) | shipped | 0.886 | 37 | 150 | 5,427 |
| other sites (441) | retrained | 0.874 | 45 | 193 | 6,242 |

Documents held (line F1 0.903 against 0.904). Other sites lost more than one seed's noise, so the weights were not
exported. Not yet known why; the candidates are seed noise, the staves `IGNORE` training, and the grown corpus itself
(its 242 new pages lean to Ultimate Guitar and a few large sites). The shipped report has only the both variant, so the
pages-only experiments above cannot be set against it.

## Done along the way (committed)

- **Long tabs keep their second half** (Ultimate Guitar's *Nothing Else Matters* lost 107 of 478 song lines): the crop
  keeps every tab staff after the song's first line and up to 8 lines between two staves, whatever the model says
  (`CropRule.kt`'s staff rule, the gate's `tab_lines`); with the shipped weights that page loses 10, and the corpus 517
  song lines instead of 614. A staff labelled clutter after a song's start trains as `IGNORE` (`gate.py`'s
  `staves_ignored`): some Ultimate Guitar pages print the tab twice, one copy clutter. That half ships with the next
  retrain.
- **Raised inline chords** (spiewnik.wywrota.pl, chords-and-tabs.net, kytaristka.cz, beausoleil.free.fr): the page read
  finds chord elements set above a lyric's baseline by their geometry and writes each such line as a chord row over its
  words (`PageSnapshotScript.kt`, `PageText.js`, and `collect.py`, which now reads the app's script). Those sites' 33
  pages were read again and 26 relabelled. gitarkotta.com's chords sit on the baseline, in red, and stay glued.
- **`content-visibility`** is forced visible for the moment of the read (link read, Safari's share, and the corpus).
- Converter rules from a measurement of the hand-written line rules over the import corpus (1.3% of lines wrong; not
  worth labels on the GRU): a trailing `[2x]` on a chord row, the ChordPro program's plain-text export, numbered verses
  starting at 2 or written `1.Word`, and a songbook's contents page dropped as a song.

## Where things are

- **Corpus** (`campfire.pageCorpus`): `pages/` and `labels/` are what today's gate, export, and `PageCorpusTest` read.
  Every page but ten carries `marks` (step 2); `marked/` is the experiment's copy, now superseded.
- **The experiment** (`marked/spike/experiment/`, outside git): the gate, `lines.py`, `model.py`, and `synthetic.py`
  with marks (`gate.py --marks size,bold,italic,monospace,heading,link,newBlock,landmark --group-by page|site --out
  <run> --variants "pages only"`, run from that folder; each marked page also trains unmarked, so a run takes about
  twice the plain one, 3 to 5.5 hours here), `rescore.py` (scores a run's predictions with the repository's current
  gate), `compare.py` and `sitescore.py` (pages every run scores; per site, Ultimate Guitar first), the six runs' reports
  (A3/B3/E3 page-grouped, C3/D3/F3 site-grouped: none, every mark, link/new block/landmark), `collect_marked.py` and
  the teacher helpers that grew the corpus, and `raised-chords.js`.
- **The marks script** is now part of the app's read, `PageSnapshotScript.kt` (the same as `PageText.js`);
  `marked/spike/page-marks.js` is the spike it came from.
- The experiment's `lines.py` adds three features (22 in all) and fills the page's eight layout inputs from the marks:
  `size` (over the body's, capped at 3, divided by 3), `bold`, `italic`, `monospace`, three zeros (`page`, `column`,
  `indent`), `heading`, then `link`, `newBlock`, and `landmark`. A line `unaligned` or with no marks gets zeros.

## Next session

1. **Explain the plain retrain's loss on other sites** before anything ships: two both-variant gates side by side on
   the current corpus, today's recipe with `--seed 11`, and the same without `staves_ignored`. Compare each with
   `gate-official2` and `gate-shipped` on the unchanged pages (Ultimate Guitar apart). If the grown corpus is the
   cause, balance it (for example weight sites so the large ones do not dominate) before the marks.
2. **The script** (done): `PAGE_SNAPSHOT_SCRIPT` and `PageText.js` return `marks`, one per line of the final text, a
   line the raised-chord rewrite splits giving both halves its marks (the words half `newBlock` 0); `collect.py` writes
   them into `pages/<id>.json`. On 30 corpus pages the text is unchanged and the marks are `page-marks.js`'s. Kotlin's
   `lines()` also splits on a lone `\r`: the decoding (step 6) splits on `\n` only. The corpus (done): `pages/` and
   `labels/` now hold the marked read, the old ones in `.runs/before-marks/`. 905 of 915 pages have marks (amdm.ru's
   three turn the browser away, abcnotation.com's seven did not answer); a page whose `marked/` copy was today's read
   took it and its carried labels, the 45 that were not (the raised-chord sites, and the 16 with no marked copy) were
   read again, and six whose chord rows the raised-chord read now splits were labelled again by the teacher (agreement
   0.98 to 1.0). The raised-chord re-read of 2026-11-25 had covered only spiewnik.wywrota.pl and chords-and-tabs.net
   (33 pages); kytaristka.cz's and beausoleil.free.fr's were read again here.
3. **The model in the repository**: the experiment's `lines.py` and gate changes into `tools/page-model` (marked pages
   trained twice, the gate scoring unmarked pages too).
4. **The gate that decides**: both variant, three models on the same corpus (no marks, every mark, every mark given
   unmarked pages), each against `gate-shipped` on unchanged pages, Ultimate Guitar first, by the rule in *Which pages
   count most*.
5. **The port**: `LineFeatures.kt` and `LineRules.kt` follow `lines.py`, `PageModelWeights` accepts the new feature
   count with `export.py`'s `VERSION` bumped, the fixtures are regenerated (`export.py --fixtures`), and the weights
   are exported.
6. **The plumbing**: `PageSnapshot` and `LoadedPage.Read` carry the marks (the iOS share inbox only gains a field), and
   `PageCropper.songLinesOf` and `PageTextCrop` take them as each line's `PageLineLayout` and new marks. Breadcrumbs say
   whether a page came with marks and how many lines aligned (never its text).
7. **Check**: `PageCorpusTest` reads the marks from the corpus pages, the import of the gitartab.hu page loses its
   partner card, and `ImportCorpusTest` is unchanged.

Open on the side: lecarnetdenotes.com's *La Bohème* (`2b9396b6433a`) loses half its song lines in some runs, and a
ChordPro modern3 PDF (*Danny Boy*) interleaves its margin chord diagrams with the song.
