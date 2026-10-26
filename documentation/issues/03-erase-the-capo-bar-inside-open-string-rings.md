<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Leave the capo bar and the string out of an open string's ring by clipping them, rather than painting the ring's inside the surface color

**Challenged:** amended — the erase-in-a-layer fix (a new `cutsRingsOut` parameter, an offscreen layer per capoed
`ChordDiagram`, a layer per capoed cell in `SongChordsSection`, and the PDF kept on its white fill) is replaced by a clip:
the bar and the strings are drawn with the rings' discs clipped out (`ClipOp.Difference`), so nothing is painted inside a
ring at all. One change in `drawFretted` serves every caller, the PDF included (its page is white behind the clip, so it
prints exactly as today), with no layer: no offscreen buffer per diagram in the Chords tab's lazy grid, the scale
sheet's chord rows, or a song page full of capoed diagrams, and no new parameter. The title changed with it.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (most visible in the dark themes)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagram.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongChordsSection.kt` (a KDoc at most),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagramDrawingTest.kt` (new)

## Problem

Under a capo, an open string's ring sits on the capo bar and is cut out of it by painting a disc in `backgroundColor`
first (`ui/chords/ChordDiagram.kt:151-153` at 54829e6fd):

```kotlin
fun openRing(string: Int, color: Color, width: Float) {
    if (geometry.capoRow != null) drawCircle(backgroundColor, radius = markerSize + width / 2f, center = Offset(x(string), openY))
    drawCircle(color, radius = markerSize, center = Offset(x(string), openY), style = Stroke(width))
}
```

`backgroundColor` is `MaterialTheme.colorScheme.surface` (`ChordDiagram`, and `ChordCellPaint` for the song's Chords
section), but the diagrams sit on other surfaces: `surfaceContainerLow` cards (`ChordLibraryCell` on the Chords tab and
in the scale sheet's chord rows, `ChordShapeGrid`'s options, `OnboardingChordPreview`), and the song's section cards
(`surfaceContainerHigh`, `SectionCards.kt`). Each ring then shows a disc of another color, plainly in the dark themes.

An erase (`BlendMode.DstOut`, as `ScaleDiagram` does) needs a layer of its own around every caller, and the PDF page has
none: `PrintRenderer.draw` draws straight onto its bitmap and reads it back as luminance with the alpha ignored, so an
erased disc would print black. A clip has neither problem: what is not drawn needs no layer and shows whatever lies
behind, a card on screen and the white page in the PDF.

## Fix

1. In `drawFretted`, where `geometry.capoRow != null` and at least one marker is `OPEN` or `OPEN_ROOT`, build one `Path`
   of the rings' discs (an oval per open string, centred at `Offset(x(string), openY)`, radius `markerSize`, the middle
   of the ring's stroke, so that the stroke covers the clip's edge however the platform antialiases a clip), and draw the
   **strings and the capo bar** inside `clipPath(rings, ClipOp.Difference) { … }`. That needs `markerSize` and `openY`
   computed before the strings are drawn (they depend only on `top`, `stringGap`, and `y`): move those two lines up. The
   fret lines are left unclipped (the capo row sits between two of them, and the ring is narrower than a row), and so are
   the dots and barres, which never sit on the capo row.
2. `openRing` loses its `backgroundColor` disc and draws only the stroked circle. The comment above it ("cut out of it")
   says the bar and string are left out inside the ring, so it reads as over the nut.
3. Nothing changes for the callers: `ChordDiagram`, `SongChordsSection.drawChordCell`, `ScaleDiagram`'s keyboard, and
   `PrintRenderer` call `drawChordDiagram` as today. `SongChordsSection.withLayerAlpha`'s KDoc names "inside a ring" among
   the places a diagram paints the background over its own lines; drop that example (a finger's number still is one, in
   the sheet) — the layer stays, since the crossfade still needs one picture per side.
4. `ui/chords/CLAUDE.md`: in the paragraph ending "…its open strings ringed on the bar (they ring at the capo) as over
   the nut without one…", say the bar and string are clipped out inside each ring, so the card (or the PDF's page) shows
   through. One clause; the file is past its word budget.

## Tests

A `desktopTest` (`./gradlew :presentation:desktopTest`), `ui/chords/ChordDiagramDrawingTest.kt`, rasterizing with
`CanvasDrawScope` into an `ImageBitmap` as `PrintRendererTest` does (its `TextMeasurer(createFontFamilyResolver(),
Density(1f), LayoutDirection.Ltr)`): a capoed geometry with an open string under `capoRow` (for example
`chordDiagramGeometryOf(ChordVoicings.default(G, GUITAR)!!, ChordTuning.GUITAR, root = 9, capo = 2)`, an A played as a G
shape above the second fret, whose B and G strings ring open) drawn over a **transparent** bitmap leaves the pixel at an
open string's x on the capo row fully transparent, while the pixel at the same row between two strings (the bar) is not.
If the drawing's constants are private, find the capo row as the row of the bar's pixels at the left edge of the grid
and the string's x from the string lines above the bar.
