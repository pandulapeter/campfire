<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Draw a system's solid strokes as one path per width, with their stroke styles built once

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPainter.kt`,
`…/ui/notation/DrawNotationSystem.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPaintingTest.kt`

Lane D, after plan 06.

## Problem

`NotationPainter` gives every stroked shape its own path and `NotationStrokePaint`, while fills are merged into one
`Path`:

```kotlin
strokes += NotationStrokePaint(path = path, width = stroke.width * shape.transform.scale, dashes = stroke.dashes?.map { it * shape.transform.scale })
```

and `drawNotationSystem` allocates a `Stroke` and a `PathEffect` for each of them on every draw:

```kotlin
system.strokes.forEach { stroke ->
    drawPath(path = stroke.path, color = color, style = Stroke(width = stroke.width, pathEffect = stroke.dashes?.let { PathEffect.dashPathEffect(it.toFloatArray()) }))
}
```

A system has a stroke per staff line, stem, bar line, tie, and box. Measured: drawing 33 systems onto a CPU bitmap took
13.5 ms, and the screen draws them again on every frame of a cross-fade, a glide of a section without a cached layer,
and a theme change.

## Fix

1. In the painter, merge the strokes of a system that have **no dashes** into one `Path` per exact stroke width (in
   the order they come; staff lines, stems, and bars share a few widths), as the fills are. Keep a **dashed** stroke as
   its own entry: a dash pattern over a merged path would run on across contours unless proven to restart per contour,
   which is not worth a test for the rare dashed slur.
2. Build each entry's `Stroke` (with its `PathEffect` where dashed) once in the painter, off the main thread, and keep
   it in `NotationStrokePaint` (e.g. `val style: Stroke`); `drawNotationSystem` passes it as is. A `Stroke` is an
   immutable draw style, safe to share across draws.
3. The box strokes the painter adds (`BOX_STROKE`) join the solid entry of their width.

Both the screen (`SongNotationBlock`) and the PDF (`PrintRenderer.draw`, which rasterizes pages through the same
`drawNotationSystem`) get it; nothing else reads `strokes`. The output is the same geometry. Where two strokes of one
width overlap, one merged path covers the overlap once instead of twice: identical in an opaque colour (the PDF's
black, the screen at rest) apart from antialiased edge pixels, and better during a cross-fade, where a translucent
colour no longer darkens the overlaps.

Must not break: drawing order fills → strokes → knockouts → texts (a tab's frets still cut its strings); colours only
given at draw time (a theme cross-fade lays nothing out again); `PrintRendererTest` and `PdfRoundTripTest`.

## Tests

- `NotationPaintingTest`: a system with three solid strokes of one width and one of another paints two stroke entries;
  a dashed stroke stays its own entry with a dash effect; the merged path's bounds equal the union of the parts'.
- The existing painting, print renderer, and PDF round-trip tests pass.
- Confirm the gain with the sweep's measurement method (timing the draw of song C's 33 systems onto a CPU bitmap, an
  untracked test deleted afterwards), against the measured 13.5 ms. Lane report only.
