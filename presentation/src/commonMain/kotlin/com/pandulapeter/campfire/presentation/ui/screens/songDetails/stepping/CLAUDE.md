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

Pedal stepping and snapping, over the `SongRows` the layout reports (`ui/songView/`).

## Snapping and stepping

- **A fling rests the top of the screen exactly below a row's divider**; a row taller than the screen is landed on at
  its top, then scrolls freely. Rows are measured by content, never trailing space. The top fade is the scroll past the
  current row's top, so a rested row is unfaded.
- Step stops are rows or pages, or sections in an unpaged single column, each `EDGE_FADE_SIZE` above its section.
- Inside a stop taller than the screen a step pages: the screen less the top fade, the bars, and two lines (at most a
  third), starting at a line; it moves on only once the stop's end is in view. Back from mid-stop, where the fade covers
  song, it moves at most a page.
- A press moves at least a line where that skips nothing; the last press hands off only once the whole song is on
  screen. **A press mid-step steps on from its target**: two quick presses are two steps.
- Previous leaves only at the very top; next only once the end has been on screen (in a setlist on the press after,
  `../CLAUDE.md`); a song that fits has neither. In a setlist they go on to the next song's top and the previous song's
  end (that page's scroll is set to its end beforehand, else a second press skips it).
- **The buttons never cover a line**: a scrolling song is laid out narrower (shorter under a row of them, below); the full width is kept where the song fits
  as one row and the full-width grid does too, never in a setlist. An unpaged single column is assumed to scroll.
- The dots count reachable stops.
- **A strip turned by column** (`SongRows.isAcross`) is the same machinery on a sideways scroll (`songPageScroll`):
  stops are column starts, counted from the song's start edge (the scroll reversed where song and screen read
  differently); the viewport and reading window are handed over across (`scrolledViewport`, `ReadingWindow.across`),
  so a height change re-lays out the strip. It leaves a column gap at the end of the screen and fades out over it and
  the page's padding, so the next column slides in out of the fade. The scroll is sideways from the first frame where
  the preference asks for it, and turns down once the layout says the song is no strip.
- **Turned by column, the buttons and dots are one row along the bottom** (`StepControls`), above the setlist's pager
  bar: previous first, in a strip's reading direction, for every song so turned, a strip or not, so paging a setlist
  never moves them. The song takes the full width; its bottom padding and reading window leave the row's height
  (`STEP_ROW_HEIGHT`), so the row covers no line.
- **Tap to step**: top half previous, bottom half next (on a strip left and right, mirrored for a song read from the
  right), only for taps nothing inside consumed; never a long press, a right or middle click, a fling-stopping press,
  or one the system cancelled. A drag under 250 ms and a tenth of the height (a strip's width) is still a tap (a
  strumming hand slides), stepping from where the song was at finger-down and replacing the fling.
- **A fling goes no further than one step**; a drag goes anywhere. Not in a column scrolling by itself (`autoScroll/`).
- The buttons take no focus; the screen retakes lost focus while uncovered, or the next arrow is spent on a focus search.
- **A held step key is one step until released** (window focus loss counts as a release): key repeats would chain
  pages. A held arrow still scrolls where there are no buttons.

## Keeping the reader in place

- The scroll keeps pixels across a resize, pinch, fold, or transposition, so at rest a `ReadingAnchor` (the stop's first
  section and the offset past it) is recorded and, inside a tall stop, a `LineAnchor`. Once a new layout has held still
  for 250 ms it scrolls there, snapped. Anchors are taken at rest and after each frame a song scrolls by itself, never
  from its own moves nor while a new layout waits to be followed, so a pinch through many layouts returns to the
  starting row.
- A height-only change (metronome panel, title row collapse) only changes row trailing padding, so it is followed in the
  same layout pass, before the scroll places the song: the divider never slides into view.
