<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :libs:pagesong

The page model's forward pass and the crop it decides (`tools/page-model/CLAUDE.md`): which lines of a
web page or a document are the song. Pure Kotlin on every platform, no dependency, no resources: the caller passes the
weights' bytes to `PageSong`, which only answers line indices and never changes a line's text.

- **Labels**, in the model's output order: `JUNK`, `SONG`, `TITLE`, `ARTIST`, `METADATA` (`PageLineLabel`).
- **Inputs**: each line's first 160 visible code points (format characters, category Cf, dropped), hashed into the
  weights' bucket count; 22 features (`LineFeatures.kt`, `LineRules.kt`); and the source, page or document. A document
  line's layout inputs come from its `PageLineLayout`, capped as in training (`page`, `size`, `indent`; not `column`),
  and its three page-only ones are zero. A page line's come from its `PageLineMarks` (the app's page read: size, bold,
  italic, monospace, heading, then link, new block, landmark; page, column, and indent zero), and are all zero without
  them (a paste, a line the read could not align), as training also saw every marked page without its marks.
- **Two weights files, one format**: the app ships a model trained with the marks, for a page read with them, and one
  trained without (`export.py --no-marks`), for everything else, since the marked one crops pastes and documents worse
  (`PageCropperImpl` in `:presentation` chooses). The plain one's mark inputs were zero in all its training, so it is
  never handed marks.
- **Python is the definition.** `tools/page-model/lines.py` (`visible`, `features`, its patterns) and `model.py`
  (`hashed`, `PageModel`) are what the model was trained on; a change there is ported here in the same change and the
  fixtures regenerated (`export.py --fixtures`, then copied into `src/desktopTest/resources`). Lengths are code points
  as Python counts them, `\s` is Python's `str.isspace`, `\b` and `\w` are Unicode, and ignore-case is Python's simple
  case folding; the patterns are written so every engine (JVM, Kotlin/Native, the browser's) reads them alike, and
  `LineFeaturesTest` runs them on each. Past the BMP, only CJK ideographs count as letters.
- **Weights format**: `tools/page-model/export.py`'s docstring and `write`/`tensors`; `PageModelWeights` refuses another
  magic, version, feature or label count, or size. The padding bucket's embedding is the weights' row 0, as PyTorch
  reads it, so convolutions past a line's end are not zero.
- **The crop** (`CropRule.kt`) is `gate.py`'s `unsure` rule: a line is kept when its likeliest label is `SONG` or
  `METADATA`, or its chance of either (rounded to four decimals) is at least 2%; the lines hold a song when at least 4
  kept lines are likeliest `SONG`, else every line is kept. Where it holds one, every tab staff string after its first
  `SONG` line (`staffLinesOf`, looser than the model's feature) and the up to 8 lines between two are kept too, whatever
  the model says: a long tab between players' notes loses its sense of where the song goes on. Blank lines (Python's `strip()` empties them) are left out
  of all of it, as the gate leaves them out (the model was not trained on them); the caller puts back those between kept
  lines. The title and artist are the surest of the printed lines likeliest so; `headerLines` are those of them heading
  the song (before its first printed `SONG` line, not running straight into it, and not kept), which no gate scored: the
  rest may be misjudged song lines and stay with it. Only the first 1,500 lines are read; the rest are kept only where
  those hold no song. `PageCrop.of` holds a part of lines scored as a whole (one song of a document) to the same rule,
  and `PageLineScores.isUnsure` is the 2% threshold for a caller keeping more lines by it. A document's lines and layout
  are `:libs:chordsheet`'s `ChordSheetPageLines`, which the trainer's documents were dumped through.
- **A retrain is judged by the gate** (`tools/page-model/gate.py`): its held-out score on pages and on documents against
  the last model's, before the new weights ship. The golden tests here prove only that Kotlin computes what PyTorch does.
