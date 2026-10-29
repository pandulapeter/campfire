<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Place a right-to-left song's music on the same side on screen as in the PDF

**Kind:** consistency · **Severity:** low · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongSectionContent.kt`
with `SongTabBlock.kt`, `SongNotationBlock.kt`, and `SongGridLine.kt` beside it (option A) or
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt` (option B), and the
`CLAUDE.md` of the package changed (`ui/songView` or `ui/print`)

**Challenged:** amended — option A as written is a no-op: `SongTabBlock` and `SongNotationBlock` always take the full
width they are offered (`constraints.maxWidth`) and draw from x = 0, and a grid is a `fillMaxWidth` `FlowRow` per line,
so an outer `Box(contentAlignment = CenterEnd)` moves nothing; aligning slot by slot would also break the run's columns.
Nothing on the page scrolls sideways (tabs wrap), so the "horizontal scroll" check was wrong. Option A now shifts the
drawing inside each block by the run's own extent, as the PDF's `atLineEnd` does; tests point at the existing
`SongDirectionTest`.

**Decision:** awaiting the user (README, Decisions D2); the recommended option is below.

## Problem

On screen, a right-to-left song's tabs, grids, and engraved tunes are laid out inside
`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` with the run filling the width, so they
start at the column's left edge. In the PDF the same blocks move as one block to the right edge (`PrintRows.atLineEnd`,
and `notationRows`' `val x = if (isRightToLeft) lineWidth - pageWidth * scale else 0f`). A Hebrew song with a tab looks
different on screen and on paper.

## Fix — options

- **A (recommended): the screen follows the PDF.** The music itself stays left to right; only the run's block moves to
  the song's start edge, as one block, exactly as `PrintRows.atLineEnd` does (shift = offered width − the run's widest
  extent, never below 0, so a run that fills or wraps is unchanged). The song's direction must be read *before*
  `SongSectionContent`'s `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` (or from
  `SongLyricsModel.textDirection`) and passed in as a flag (`isAtEnd`), since inside the provider it is always LTR.
  - **Tab and preformatted runs** (`SongTabBlock`): in `drawBehind`, offset every drawn row and staff by
    `size.width - extent`, where `extent` is the widest row of `rows.at(width)` over **all** slots of the run (not just
    this slot's rows), so the run's systems stay lined up when the page layout puts its slots in different columns.
    The `Layout`'s measured size, its intrinsic answer (the whole run unwrapped, which the column planning relies on),
    and the semantics stay as they are.
  - **Engraved tunes** (`SongNotationBlock`): offset `drawSystems` by `size.width - pageWidth * scale` (the leaving
    rendering of the crossfade by its own page width), mirroring `notationRows`' `x`. In practice this moves only a page
    narrower than its column (the tune is rendered for the widest column).
  - **Grids** (`SongGridLine` in the `else` branch): shift every line of the run by the column width minus the widest
    aligned line's unwrapped width (measured once per run, beside `gridBars`), e.g. a start padding on each line's
    `FlowRow`; 0 once any line wraps, so wrapped bars keep their columns.
  - The fold toggle row is the song's and stays where it is; check the crossfade between text and notation and the fold
    animation with the shifted drawing.
- **B: the PDF follows the screen.** Drop `atLineEnd` and the `isRightToLeft` branch of `notationRows`'s `x`, and the
  "move to the right edge as one block" sentence of `ui/print/CLAUDE.md`'s Direction section. Smaller, but the paper
  then has music starting on the opposite side from the song's lyrics.

Whichever is chosen, update the Rendering bullet "Music is always LTR" in `ui/songView/CLAUDE.md` (option A: "…drawn
from the song's start edge as one block") or the print Direction section (option B).

## Tests

Extend `tools/screenshots`' `SongDirectionTest` (it already renders the Hebrew fixtures): under option A, an RTL song's
short tab, short grid, and narrow tune are drawn ending at the column's right edge, every slot of a two-slot tab with
the same shift, and a tab wider than the column wraps exactly as before; an LTR song is unchanged. Update the goldens
that move. Under option B, add `PrintLayoutTest` cases (it has no right-to-left one today) that an RTL song's tab and tune start at x = 0.
