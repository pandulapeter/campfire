# Floor a chord diagram's lines at one device pixel rather than one drawing unit, so the PDF's diagrams are not drawn in 1 pt lines

**Kind:** bug (print quality)  ·  **Severity:** low  ·  **Platforms:** all (PDF export and its preview)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChordDiagram.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`, `presentation/CLAUDE.md`

## Problem

`drawChordDiagram` (`components/ChordDiagram.kt`) is shared by the screen and the PDF. Its line widths are a fraction
of the diagram, floored at `1f`:

```kotlin
// drawFretted
val thin = (min(stringGap, fretGap) * LINE_WIDTH).coerceAtLeast(1f)
// drawKeyboard
val thin = (keyWidth * KEY_LINE_WIDTH).coerceAtLeast(1f)
```

On screen a unit is a pixel, so the floor is one pixel. The PDF draws in points under a scale
(`PrintRenderer.draw`: `scale(scale, scale, pivot = Offset.Zero) { … drawChordDiagram(…) }`, `scale = 3f` for the
216 dpi page images), so there the floor is **1 pt**, three page pixels. Traced at a 10 pt print size: a fretted
diagram is `3.5 × 10 = 35` pt wide, `stringGap = (35 − 7.7 − 2.8) / 5 ≈ 4.9` pt, `thin = 4.9 × 0.08 ≈ 0.39` pt, floored
to 1 pt — strings and frets 2.5 times as heavy as designed, the nut (`NUT_WIDTH = 3`) 3 pt. A keyboard is
`4.75 × 10 = 47.5` pt over 14 white keys, `keyWidth ≈ 3.4` pt, `thin ≈ 0.41` → 1 pt, which is 30 % of a key's width
(37 % at 8 pt): the key lines and the black keys' outlines eat the fills the chord is read by.

## Fix

Give `drawChordDiagram` the floor as a parameter, in its own units:

```kotlin
internal fun DrawScope.drawChordDiagram(
    geometry: ChordDiagramGeometry,
    area: Size,
    ...,
    showsFingers: Boolean = false,
    /** The thinnest a line is drawn, in the drawing's units: one pixel of whatever the drawing ends up on. */
    minimumStroke: Float = 1f,
)
```

passed to `drawFretted` and `drawKeyboard` and used in place of both `coerceAtLeast(1f)`. The screen keeps the default.
`PrintRenderer.draw` passes `minimumStroke = 1f / scale` — one pixel of the bitmap at whatever scale it is drawn
(3 for the PDF's pages, the preview's own scale for the preview), so the preview and the file agree. Mention it in the
KDoc of `drawChordDiagram` ("…and its thinnest line is [minimumStroke], one pixel of the device it ends up on") and in
the PDF paragraph of `presentation/CLAUDE.md` where it says diagrams are drawn by the screen's own `drawChordDiagram`.

## Tests

None by code: the stroke is drawing. (`PrintLayoutTest` covers the layout, not the pixels.)

## Manual check

Export a song with chord diagrams at the smallest and the default text size, on the guitar and on the keyboard, and
zoom into the PDF: the strings and frets are hairlines with a visibly heavier nut, and the keyboard's pressed keys
are clearly filled between thin key lines, as on the screen.
