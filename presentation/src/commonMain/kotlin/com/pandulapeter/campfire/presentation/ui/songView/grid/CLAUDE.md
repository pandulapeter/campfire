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

Pure, over measurements; the layout only measures and places. Pedal reading drives every rule: each line reachable
with the step keys, fewest pages first.

- **Fewest (widest) columns that fit the song in one row**, else the fewest that fit it in several, from intrinsic
  heights. Column width 384–560dp scaled by `fontScale` (288dp narrow). Columns narrow to their widest content, but a
  row is centered by the widths its columns were given, since where lines wrap jumps as the text grows and would move
  the columns sideways. A row of fewer columns (the last page) starts at the fullest row's start edge.
- **Several counts fit one row → the most even fill**; more columns only by a margin and wrapping no more lines.
- **A page with room spreads out**: section gaps widened first (by at most a chorded line), then a single-row page's
  columns centered as one block by the tallest, 45% above, since exact centering reads low. They share one top, so the
  eye finds the next column's first line where the last one's was. The room is the one the grid was decided for, so it
  never scrolls. `ColumnSpreads` is what the fold hold freezes.
- Rows are read across like systems of sheet music, never column by column. **A section taller than the screen gets a
  row of its own**: a pedal reader cannot go back up to a column beside it.
- Fewest pages first, then the row reaching furthest; columns balanced by squared deviation, since greedy fill dumps
  the slack on the last column.
- A section wider than a column (a tab staff) may take a wider row where that saves a page; neighbours may stack in that
  row's single column.
- **A song not read whole in one row is set like a magazine**: columns filled to the screen, sections running on, since
  every early page end is an extra turn; priced cuts (`LayoutScoring`), pages then evened out. Compared with the
  whole-section layout by page count, then score; a full tie keeps sections whole.
- **Narrow columns** only for strictly fewer pages, where at most a quarter of lyric lines wrap.
- **The text is always the reader's size**, never set smaller to save a page: fitting was too costly while a pinch or a
  window drag kept coming, and fitting once it stopped rearranged the song after the reader had.
- **A single-column width is paged** with the same scoring. Two neighbours that fit half the column unwrapped may sit
  side by side and are never cut. Something uncuttable moves to the next page only if this one is at least half full.
- **Chunks**: cuts only where `sectionChunkStarts` allows (a line each side, never after a comment or before an empty
  line). Every tab slot's intrinsic width is the whole run's, or narrowing a column to one slot would rewrap the run. A
  folded section is one chunk; the preview never cuts.
- **A chunk's height is kept per width** (`SectionMeasurements`), and for one past its widest line once for every
  width there, where its kind is proven as tall at all of them (`flatPastWidestOf`, each kind probed by a test): lines,
  comments, key changes, tabs, grids, headings, and timing lines. Never a chunk holding a tune, nor the Chords, Scales,
  or first sections; a kind is added only with its probe.
- **Tempo and time changes**: each stretch between them is searched as a song of its own, each starting a page, never
  half of a side-by-side pair.

### Turned by column (`PageTurn.COLUMN`)

- A song the page search would step through is set as one **strip** instead (`flowIntoStrip`): the readable column
  count for the width less a gap the screen fades it over, every column filled to the screen by `ColumnFlow`'s priced cuts, fewest
  columns first, never evened out. A song read whole keeps its ordinary grid.
- Gone in a strip: a section taller than the screen getting a row of its own (it is cut across columns), side-by-side
  pairs, centering and gap spreading. A tempo or time change starts a column. Narrow columns only for fewer steps,
  under the quarter-of-lines rule.
- A section that does not wrap (a tab staff) widens its column up to the view less a gap, taking whole slots (centered
  in them, so the columns in view still fill it); an engraved tune is not one: it asks the narrowest page as its least
  width, and is engraved for the strip's own column. Each column is centered in a slot of the view and a gap
  (`placeStrip`), so neighbours start past its edge, and a step moves by the leaving column's slot. **The strip ends at the first stop that shows its last column**: no last step shows nothing new.
- **Something uncuttable taller than a column leaves no strip**: the song is paged as before, decided per song, since a
  strip has no column that scrolls.
