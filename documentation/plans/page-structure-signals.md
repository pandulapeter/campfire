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

song's chords turns up, and the gate runs again over it.
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

## Steps

1. **The model**, first as an experiment on copies of the scripts, outside the repository: `lines.py` reads the marks
   into the page's layout inputs and the new features, `synthetic.py`'s made-up pages get zero marks, and the gate
   trains on marked pages and their zeroed copies. Gate against today's report (`.runs/gate.txt`, kept aside first),
   adding the unmarked-pages score; drop any mark that does not earn its place. The gate also scores with folds grouped
   by site, since `teacher.py` makes each page its own group and a held-out page's siblings from the same site are
   otherwise trained on: marks are mostly a site's template, so only the site-grouped score says how they carry to a
   site never seen (the corpus has 174 sites, 85 with one page). Where it falls well below the page-grouped one, the
   corpus needs more sites before the marks ship. If the marks hold up: `collect.py` takes the script and writes
   `marks` into `pages/<id>.json`, and `marked/` replaces `pages/` and `labels/`, the old ones kept aside.
2. **The port**: `LineFeatures.kt` and `LineRules.kt` follow `lines.py`, `PageModelWeights` accepts the new feature
   count with `export.py`'s `VERSION` bumped, the fixtures are regenerated (`export.py --fixtures`), and the weights
   are exported.
3. **The plumbing**: the shared script into `PAGE_SNAPSHOT_SCRIPT` and `PageText.js` (the iOS share inbox only gains
   a field), `PageSnapshot` and `LoadedPage.Read` carry the marks, and `PageCropper.songLinesOf` and `PageTextCrop` take
   them as each line's `PageLineLayout` and new marks. Breadcrumbs say whether a page came with marks and how many
   lines aligned (never its text).
4. **Check**: `PageCorpusTest` reads the marks from the corpus pages, the import of the gitartab.hu page loses its
   partner card, and the documents' scores are unchanged.
