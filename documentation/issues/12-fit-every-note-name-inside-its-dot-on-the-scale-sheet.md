# Size the scale sheet's dot labels so the widest one of the scale fits inside its dot

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (worst on phones)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleDiagram.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleLabelSize.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleLabelSizeTest.kt` (new)

**Challenged:** sound — the numbers hold at HEAD (`fretGap` 328 / 12.62 ≈ 26 dp, `rowGap` (148 − 16) / 6 = 22 dp, radius ≈ 10 dp), the filled label is drawn in `colorScheme.surface` on the dot, `ScaleDiagram` has only the two callers named (the cells pass `labels = null`), and `LABEL_TEXT` has no other declaration in `ui.scales` to clash with once it is `internal`. One care for the builder: measure the widest label with its own `TextStyle` at the default size, not through `label(...)`, whose cache would then hold it at the wrong size.

## Problem

`ScaleDiagram.kt`'s `drawFretboard` sizes every dot label from the dot alone:

```kotlin
val radius = min(fretGap * DOT_RADIUS, rowGap * DOT_ROW_RADIUS)
...
val labelSize = (radius * LABEL_TEXT / density / fontScale).sp
...
labels?.getOrNull(note.degree)?.let { text ->
    val layout = label(text, if (isOpen) color else backgroundColor)
    drawText(layout, topLeft = Offset(dotCenter.x - layout.size.width / 2f, dotCenter.y - layout.size.height / 2f))
}
```

`LABEL_TEXT = 1.05f`, `DOT_RADIUS = 0.42f`, `DOT_ROW_RADIUS = 0.46f`, and `FRET_COUNT = 12`, with labels in `FontWeight.Bold`. On a 360 dp phone the sheet's neck works out like this:

- The neck is 328 dp wide (16 dp padding). `fretGap` = 328 / 12.62 ≈ 26 dp.
- The six-string `sheetHeight` is 16 + 22 × 6 = 148 dp, so `rowGap` ≈ 22 dp.
- The radius is min(10.9, 10.1) ≈ 10 dp, so a dot is ~20 dp wide and its label is ~10.6 dp bold.

A filled dot's label is drawn in `MaterialTheme.colorScheme.surface` on the dot. Whatever spills past the dot lands on the sheet's `sheetContainerColor()` in nearly the same colour and disappears.

The labels too wide for that dot are:

- **Latin note names**, in Notes mode with the Latin notation: `shownScaleNoteNames` gives "Sol♯", "Sol♭", "Do♯", "Re♭", "Mi♭", "Fa♯", "La♭", "Si♭", and "Sol". That is three or four bold glyphs, about 1.7–2.2 em, or ~18–24 dp. "Sol♯" reads as "ol♯", and the outer letters of "Do♯" and "Re♭" vanish.
- **The diminished seventh degree** "♭♭7" in Degrees mode (Ultralocrian's `bb7`) is borderline.

German notation is not affected, despite the finding's title. `ChordNotation.GERMAN` only renames `B` to `H` and `Bb` to `B`; it never writes "Fis", so its labels stay as narrow as the standard ones.

## Fix

Options:

- **Fit the widest label (recommended).** Measure the scale's labels once at the default size, and shrink the font for the whole diagram just enough that the widest fits ~1.8 × the radius. One size per diagram keeps the dots reading as a set, and a scale with only narrow labels keeps today's size.
- Drop the bold and tighten the letter spacing for labels over two glyphs. Rejected: "Sol♯" still overflows at ~1.9 em regular, and mixed weights in one diagram look accidental.
- Abbreviate the Latin names. Rejected: there is no short form in `:chordpro` (`ChordProNotation`'s `latinNames` is `Do Re Mi Fa Sol La Si`), and an invented one ("So♯") is not what a Latin reader reads anywhere else in the app.

Steps:

1. **New `ScaleLabelSize.kt`** in `ui/scales`, next to its Composable, holding a pure function (the code-style "decisions are pure functions" rule):
   ```kotlin
   /**
    * The font size of a scale diagram's dot labels, in pixels: [LABEL_TEXT] of the dot's [radius], made just small
    * enough that the widest of the diagram's labels, [widestAtDefault] pixels wide at that size, fits [LABEL_ROOM]
    * radii, since a label spilling past its dot is drawn in the surface colour over the sheet's and is lost.
    */
   internal fun scaleLabelFontSize(radius: Float, widestAtDefault: Float): Float {
       val default = radius * LABEL_TEXT
       val room = radius * LABEL_ROOM
       return if (widestAtDefault <= room) default else default * room / widestAtDefault
   }

   internal const val LABEL_TEXT = 1.05f

   /** How much of a dot's width, in radii, a label may take: its diameter less a little air at each side. */
   internal const val LABEL_ROOM = 1.8f
   ```
   Move `LABEL_TEXT` here from `ScaleDiagram.kt`, widening it from `private` to `internal`.
2. **In `drawFretboard`:**
   - Measure each distinct label at the default size with the `textMeasurer` it already has, and take the widest.
   - Compute `val labelSize = (scaleLabelFontSize(radius, widest) / density / fontScale).sp` in place of today's line.
   - The `label(...)` cache is keyed by text, colour, and number. It can stay as it is, since only one size is used per draw.
   - The extra measuring happens once per distinct label per draw, and a scale has at most 12 distinct labels, so it costs little.
   - Fret numbers (`numberSize`) are unchanged.
   - Add one sentence to `drawFretboard`'s KDoc saying the labels share the size the widest of them fits its dot at.
3. The Scales tab's cells draw no labels (`labels = null`), so they are untouched.

## Tests

`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleLabelSizeTest.kt`:

- `` `a label that fits its dot keeps the full size` ``: `scaleLabelFontSize(radius = 10f, widestAtDefault = 12f)` equals `10f * LABEL_TEXT`.
- `` `a label wider than its dot is made small enough to fit it` ``: for `radius = 10f` and `widestAtDefault = 24f`, the returned size times `24f / (10f * LABEL_TEXT)`, which is the label's width at the returned size, is within 0.01 of `10f * LABEL_ROOM`.
- `` `the widest label decides, so a wider one is never drawn larger` ``: the size for a widest of 30 is smaller than the size for 24.

That the dots look right in Latin notation is a visual check the user makes. No golden is added for it, to keep the golden set small.
