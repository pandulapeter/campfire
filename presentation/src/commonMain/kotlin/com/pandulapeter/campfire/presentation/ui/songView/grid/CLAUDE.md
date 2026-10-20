<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/songView/grid

Where a song's sections go: pure, over measurements (`SongGridSearch` over `SectionMetrics`, `placeSections`); the
layout (`songView/SongSectionsLayout`) only measures and places. Pedal reading drives every rule: each line reachable
with the step keys, fewest pages first.

- **Fewest (widest) columns that fit the song in one row**, else the fewest that fit it in several (`searchColumnCount`),
  from intrinsic heights; a failing candidate jumps to the smallest count that could fit. A single-column window skips
  the search.
- Column width 384–560dp scaled by `fontScale` (`NARROW_COLUMN_WIDTH`, 288dp, below). Columns narrow to their widest
  content, but a row is centered by the widths its columns were given (`SectionWidths.slotWidths`), the leftover shared
  evenly around them, clear of the step buttons where needed: where lines wrap jumps as the text grows, and would move
  the columns sideways with it.
- **Several counts fit one row → the most even fill** (`balancedAmong`); more columns only by a margin and wrapping no
  more lines.
- **A page with room spreads out** (`SectionGrid.arrange`'s `maxExtraSectionGap`): a single-row page's columns are
  centered, section gaps widened first (by at most a chorded line), the rest 45% above, since exact centering reads low.
  The room is the one the grid was decided for, so it never scrolls. `ColumnSpreads` is what the hold freezes.
- Rows are read across like systems of sheet music, never column by column. **A section taller than the screen gets a
  row of its own**: a pedal reader cannot go back up to a column beside it.
- `flowIntoRows`: fewest pages first (`pagesOf`), then the row reaching furthest (`isBetterRow`); columns balanced by
  squared deviation, since greedy fill dumps the slack on the last column.
- A section wider than a column (a tab staff) may take a wider row where that saves a page; neighbours may stack in that
  row's single column (`forEachWideRow`).
- **A song not read whole in one row is set like a magazine** (`flowLikeAMagazine`): columns filled to the screen,
  sections running on, since every early page end is an extra turn; priced cuts, scored by `LayoutScoring`. Pages are
  then evened out (`ColumnFlow.evenOut`). Compared with the whole-section layout by `SectionGrid.pageCount`, then
  `score`; a full tie keeps sections whole.
- **Narrow columns**: taken only for strictly fewer pages (`withNarrowColumns`), where at most a quarter of lyric lines
  wrap (`fitsNarrowColumns`).
- **Text fitting** (`TextFitting.kt`): 97.5%, then 95%, never below `MIN_FONT_SCALE`, the first that saves a page
  (`chooseFitScale`), searched only where a lower bound says it could. Drawn through a scaled layer, so nothing
  recomposes and the first frame is fitted (`DecidedGrid.fitScale`). Deferred while the inputs keep changing
  (`isFitDeferred`), since it costs two or three more layouts a frame.
- **A single-column width is paged** (`flowIntoPages`) with the same scoring. Two neighbours that fit half the column
  unwrapped may sit side by side (`SectionGrid.joinsPrevious`) and are never cut. Something uncuttable moves to the next
  page only if this one is at least half full. Rows share a start edge (`sharesKeyline`).
- **Chunks** (`SongUnits`, in `songView/model`): cuts fall only where `sectionChunkStarts` allows (a line each side,
  never after a comment or before an empty line). Every tab slot's intrinsic width is the whole run's, or narrowing a
  column to one slot would rewrap the run. A folded section is one chunk; the preview never cuts.
- **Tempo and time changes**: each stretch between them is searched as a song of its own (`SongUnits.timingStarts`,
  `searchSegment`, `SectionGrid.concatenated`), each starting a page, never half of a side-by-side pair.
