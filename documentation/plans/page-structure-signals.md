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

**Status:** not started; top priority. When the app reads a web page itself, it hands the page model each line's
structure too: whether the line is a link or a button, whether it starts a new card or section, and how it is set. The
crop can then drop calls to action and partner cards next to a song, which text alone cannot tell apart from the song's
own notes.

## Why

`https://gitartab.hu/hu/tab/tankcsapda/ez-az-a-haz/293`, read as a link, keeps "A dalok kíséréséhez ajánljuk", "Adj erőt
a Tankcsapda-riffeknek!", the first "Megnézem a Thomannál →", and the capo hint above the chords. Measured on
2026-11-24:

- **The model already calls them clutter.** The shipped weights score those lines 79–95% `JUNK`. They survive because
  the crop (`CropRule.kt`, `gate.py`'s `unsure`) also keeps any line with at least a 2% chance of `SONG` or `METADATA`
  (3.2%, 19.8%, 5.2%, and 3.8% here).
- **The margin pays for itself, and a stricter rule only moves the trade-off.** On the last gate run's held-out
  predictions (pages):

  | Rule | Mean line F1 | Song lines lost | Extra lines kept |
  |---|---|---|---|
  | 2% everywhere (today) | 0.892 | 210 | 5,828 |
  | 5% outside the song's span | 0.905 | 301 | 4,815 |
  | 20% outside the span | 0.915 | 588 | 3,918 |
  | Uncertain lines kept only between song lines | 0.916 | 728 | 3,640 |

  Every clutter line removed costs song lines at a steady rate, and a lost song line is worse than a stray one.
- **Another training run will not fix it.** The page is already in the corpus, labelled `JUNK` there, beside 29
  others from the same site. The gate's held-out model scored those lines at 0.1% and the shipped one at 3–20%, so a
  retrain only changes which pages show the clutter.
- **The model cannot see what the reader sees.** A page line's inputs are its characters and 11 text features; its
  layout inputs are zero, and `innerText` drops which lines are links, buttons, or a separate card.

## Decisions

- **The text stays `innerText`, unchanged.** The structure comes as per-line marks beside it, one per line of
  `text.lines()`, so the text the converter, the editor, and **Use the whole page** see is exactly today's. A page
  whose marks are missing or do not match its line count is cropped as today, from its text alone.
- **Only where the app reads the page itself.** The link read (`PAGE_SNAPSHOT_SCRIPT`, run by Android's, iOS's, and the
  desktop's web views), Safari's share extension (`PageText.js`), and the corpus collector (`collect.py`) all run
  JavaScript on the live page and use one shared script. Pasted, dropped, and shared plain text, documents, and the web
  build have no page to walk and keep zero marks; reading a clipboard's HTML is not part of this plan.
- **The marks, decided by the spike and kept only where the gate says they help:**
  - *link*: every visible character of the line is inside `a[href]`, `button`, or `[role=button]`;
  - *new block*: the line's nearest card (`article`, `section`, `aside`, `nav`, `header`, `footer`, `form`, `li`, or an
    element with its own border, background, or shadow) differs from the previous printed line's;
  - *landmark*: the line is inside `nav`, `header`, `footer`, `aside`, or `form`;
  - *how it is set*: the existing document layout inputs where the DOM says the same thing: `size` (its font size over
    the body's), `bold`, `italic`, `monospace` (`pre`, `code`, or a monospace font, which most chord sheets use), and
    `heading` (`h1`–`h6`). `page`, `column`, and `indent` stay zero for a page.
- **Marks are found by aligning, not by rebuilding the text.** The script walks the page's visible text nodes in order,
  each with its marks, and finds each node's collapsed text in `innerText` from where the last one ended; a line takes
  the marks of the characters on it. Where alignment fails, that line's marks are zero.
- **A page without marks must not get worse.** Training sees every marked page a second time with its marks zeroed, and
  the gate scores pages both ways. A paste is that second case, so the new model must hold up against today's on
  unmarked pages.
- **The source flag stays two-valued.** A page with marks is still a page; its layout inputs are no longer zero, which
  `libs/pagesong/CLAUDE.md` and `lines.py`'s docstring then say.

## Steps

1. **Spike the script** in the scratchpad, over a few dozen corpus pages (gitartab.hu, Ultimate Guitar, a WordPress
   blog, a page with a `<pre>` chord sheet, a table-laid one): the alignment's hit rate per line, and whether the marks
   separate the agreed labels' `JUNK` from `SONG` (how often each mark is set on each label). Stop and rethink if
   alignment misses more than a few lines a page or no mark separates them.
2. **The corpus**: `collect.py` runs the script and writes each line's marks into `pages/<id>.json` (a new field;
   `lines` unchanged). Recollect every page. Where a page's lines come back identical, its labels stand; where they
   differ, labels move over by text as `align_documents.py` aligns documents, and a page aligned too poorly goes back
   to the teacher (`teacher.py prepare` for those pages only).
3. **The model**: `lines.py` reads the marks into the page's layout inputs and the new features, `synthetic.py`'s
   made-up pages get zero marks, and the gate trains on marked pages and their zeroed copies. Gate against today's
   report (`.runs/gate.txt`, kept aside first), adding the unmarked-pages score; drop any mark that does not earn its
   place. The gate also scores with folds grouped by site, since `teacher.py` makes each page its own group and a
   held-out page's siblings from the same site (27 for gitartab.hu) are otherwise trained on: marks are mostly a site's
   template, so only the site-grouped score says how they carry to a site never seen. Where it falls well below the
   page-grouped one, the corpus needs more sites (a page or two each) before the marks ship, not more pages of the
   same ones. The gitartab.hu page above is the example to look at, not the measure.
4. **The port**: `LineFeatures.kt` and `LineRules.kt` follow `lines.py`, `PageModelWeights` accepts the new feature
   count with `export.py`'s `VERSION` bumped, the fixtures are regenerated (`export.py --fixtures`), and the weights
   are exported.
5. **The plumbing**: the shared script into `PAGE_SNAPSHOT_SCRIPT` and `PageText.js` (the iOS share inbox only gains
   a field), `PageSnapshot` and `LoadedPage.Read` carry the marks, and `PageCropper.songLinesOf` and `PageTextCrop` take
   them as each line's `PageLineLayout` and new marks. Breadcrumbs say whether a page came with marks and how many
   lines aligned (never its text).
6. **Check**: `PageCorpusTest` reads the marks from the corpus pages, the import of the gitartab.hu page loses its
   partner card, and the documents' scores are unchanged.
