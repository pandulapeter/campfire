<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Draw the banjo's drone spiked above a capo in the scale diagram, as the chord rows already play it, and keep the capo bar off it

**Challenged:** amended — step 3's veil on the drone's row started "from where the string starts", which is right of
the drone's first note (the string line begins at `center(start) + radius`), so a scale note on fret 5 under the capo
would stay unveiled; it now starts at `fretLine(start - 1)`, and no further left than `drawBelowCapo`'s own veil ends,
so the two `DstOut` rects never overlap (a second 0.7 veil over the first would fade those pixels to 0.91, unlike every
other frozen fret). Openness, the bar's rows, capo 0, capos past the fifth fret, and the scale sheet's chord rows check
out as planned.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all, banjo only
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleDiagram.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleDiagramGeometry.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleDiagramGeometryTest.kt`

## Problem

Under a capo, the scale diagram and the chord diagrams beside it in the same sheet disagree about the banjo's fifth
(drone) string, which is the bottom row and starts at fret 5 (`BANJO_DRONE_FRET`):

- `ScaleDiagram.drawFretboard` draws the capo bar across every row (`ui/scales/ScaleDiagram.kt:163-172` at 54829e6fd):

  ```kotlin
  if (capo > 0f) {
      …
      drawRoundRect(
          color = mutedColor.copy(alpha = mutedColor.alpha * capo.coerceAtMost(1f)),
          topLeft = Offset(capoX - width / 2f, top - radius),
          size = Size(width, bottom - top + radius * 2),
  ```

  so with capo 2 the bar crosses the drone's row at fret 2, where that string does not exist.
- `scaleNoteOpenness` keeps the drone's ring at fret 5 whatever the capo (`ScaleDiagramGeometry.kt:121`):

  ```kotlin
  internal fun scaleNoteOpenness(fret: Int, start: Int, capo: Float): Float = when {
      fret == start -> 1f
      capo <= 0f || fret < start -> 0f
      else -> (1f - abs(capo - fret)).coerceIn(0f, 1f)
  }
  ```

  so the drone rings open g at 5, while the chord rows of the same sheet draw it ringing on the capo
  (`ChordDiagramGeometry.aboveCapo` reads the shape as if the capo were the nut): spiked at 5 + capo, sounding a with
  capo 2. That is how a banjo player capos (a spike or railroad spike at 7 for capo 2), and the only reading under which
  a table shape's open drone note is right in the shape's key. The test pins the old reading on purpose
  (`ScaleDiagramGeometryTest`: "The banjo's drone, starting at the fifth fret, rings open there whatever the capo below
  it"); this plan replaces that assertion.

The 29th review's "a banjo drone is muted at draw time" is about chord shapes whose drone note is not in the chord; it
is untouched here.

## Fix

1. `scaleNoteOpenness`: a string starting up the neck (`start > 0`) rings open at `start + capo` (sliding with it):

   ```kotlin
   internal fun scaleNoteOpenness(fret: Int, start: Int, capo: Float): Float = when {
       start > 0 -> (1f - abs(start + capo - fret)).coerceIn(0f, 1f)
       fret == 0 -> 1f
       capo <= 0f -> 0f
       else -> (1f - abs(capo - fret)).coerceIn(0f, 1f)
   }
   ```

   (With no capo the drone's `fret == start` is still 1, and its frets below `start` are never placed.) Update the KDoc:
   a string starting up the neck, the banjo's drone, is spiked the capo's frets above its start, as a player capos it
   and as the chord diagrams draw it.
2. `drawFretboard`: draw the bar only across the rows starting at the nut (`stringStarts[row] == 0`, the top rows on a
   banjo): its `top`/`bottom` from the first and last such row. A neck whose every row starts at the nut is drawn as
   today.
3. `drawBelowCapo`: also veil a row starting up the neck from its start up to its spike, the way the frets below the
   capo are veiled on the others: a rect over that row's band (`rowGap` tall, centred on its `y`, which stays clear of the
   row above's dots, `DOT_ROW_RADIUS` being under half a row), from `max(end, fretLine(start - 1))` (the fret-5 note's
   whole cell, and never over the full-height veil already drawn up to `end`) to `fretLine(start + capo - 1)`
   (interpolated while the capo slides, as `end` already is), with `BELOW_CAPO_VEIL` and `BlendMode.DstOut`; nothing
   where that span is empty. It needs the row geometry, so pass what it needs from `drawFretboard` or compute `rowGap` the
   same way. A spike past `fretCount` veils the row to the board's end.
4. `ScaleDiagram`'s `drawFretboard` KDoc and `dialogs/scales/CLAUDE.md` (the "Opened from a capoed song" item): add that
   the banjo's drone is spiked the capo's frets above its fifth fret, ringing there as the chord rows play it, and the
   bar lies across the strings from the nut only.

## Tests

`ScaleDiagramGeometryTest` (`./gradlew :presentation:desktopTest`): in "the notes a capo stops ring open as the nut's
do…", replace the drone assertions with `scaleNoteOpenness(fret = 5, start = 5, capo = 0f) == 1f`,
`scaleNoteOpenness(fret = 7, start = 5, capo = 2f) == 1f`, `scaleNoteOpenness(fret = 5, start = 5, capo = 2f) == 0f`,
and `scaleNoteOpenness(fret = 6, start = 5, capo = 1.5f) == 0.5f`, with the comment saying the drone is spiked above
the capo. The bar's and veil's extents are drawing only; if the row range is computed in a helper (for example
`internal val ScaleDiagramGeometry.Fretboard.nutRows`), test it: a banjo's is every row but the last, a guitar's every
row.
