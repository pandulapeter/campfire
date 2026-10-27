<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songDetails/stepping

The song details screen's pedal stepping and snapping, over the `SongRows` the layout reports (`ui/songView/`).

### Snapping

- **A fling rests the top of the screen exactly below a row's divider** (`RowSnapping.kt`, rows from `SongRows`); a row
  taller than the screen is landed on at its top and then scrolls freely. Rows are measured by content, never trailing
  space. The top fade is the scroll past the current row's top (`SongRows.scrolledIntoRow`), so a rested row is unfaded.
- **Step buttons** (`StepControls`, one pair over the pager): stops are rows or pages, or sections in an unpaged single
  column, each stop `EDGE_FADE_SIZE` above its section.
- Inside a stop taller than the screen a step pages (`nextStepTarget`, `previousStepTarget`, `ReadingWindow`): the
  screen less the top fade, the bars, and two lines (at most a third), starting at a line (`lineTops`); it moves on
  only once the stop's end is in view. Back from mid-stop, where the fade covers song, it moves at most a page.
- A press moves at least a line where that skips nothing; the last press hands off only once the whole song is on
  screen.
- **A press mid-step steps on from its target**: two quick presses are two steps.
- Previous leaves only at the very top; next only once the end has been on screen; a song that fits has neither. In a
  setlist they go on to the next song's top and the previous song's end (that page's scroll is set to its end
  beforehand, else a second press skips it).
- **They never cover a line**: a scrolling song is laid out narrower (`stepButtonInset`); the full width is kept where
  the song fits there as one row and the full-width grid does too (`isReadWithoutStepping`), never in a setlist
  (`keepsStepButtonInset`). An unpaged single column is assumed to scroll.
- The dots (`StepProgressIndicator`) count reachable stops (`reachableStops`).
- **Tap to step** (`stepOnTap`): top half previous, bottom half next, only for taps nothing inside consumed; never a
  long press, a right or middle click, a fling-stopping press, or one the system cancelled. A drag under 250 ms and a
  tenth of the height is still a tap (a strumming hand slides), stepping from where the song was at finger-down and
  replacing the fling.
- **A fling goes no further than one step** (`oneStepCappedTarget`); a drag goes anywhere. Not in one column that
  scrolls by itself (`isCapped`, see `autoScroll/`).
- Focus: the buttons take none; the screen retakes lost focus while uncovered, or the next arrow is spent on a focus
  search.
- **A held step key is one step until released** (window focus loss counts as a release): key repeats would chain pages.
  A held arrow still scrolls where there are no buttons.

#### Keeping the reader in place

- The scroll keeps pixels across a resize, pinch, fold, or transposition, so `RowSnapFlingBehavior.keepReaderInPlace`
  records at rest a `ReadingAnchor` (the stop's first section and the offset past it) and, inside a tall stop, a
  `LineAnchor` (section, chunk, fraction). Once a new layout has held still for 250 ms it scrolls there, snapped by
  `settledScrollTarget`. Anchors are taken only at rest and never from its own moves, so a pinch through many layouts
  returns to the starting row.
- A height-only change (metronome panel, title row collapse) only changes row trailing padding, so it is followed in the
  same layout pass (`onRowsPlaced`), before the scroll places the song: the divider never slides into view.
