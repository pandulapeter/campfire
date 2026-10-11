# Draw the tag color popup's check mark in whichever of black and white contrasts more with the disc, by the WCAG comparison `pressedKeyColor` already uses

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TagColorButton.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Contrast.kt` (new), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChordDiagram.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/TagColor.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/ContrastTest.kt` (new), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/TagColorTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

The popup of tag colors (`TagColorButton.kt`, `tagColorOptions`, L123–129 and L160–161 at d650f3cb0) draws the
selected disc's check in black or white by a luminance threshold of 0.4:

```kotlin
contentColor = if (color.luminance() > CHECK_LUMINANCE_THRESHOLD) Color.Black else Color.White,
...
/** Where white stops being the more legible check on a disc; a little under the middle, as the eye reads it. */
private const val CHECK_LUMINANCE_THRESHOLD = 0.4f
```

Black and white contrast equally with a color of relative luminance √(1.05 × 0.05) − 0.05 ≈ 0.179, so every disc
between 0.179 and 0.4 gets the less legible of the two, white. Computed from the `TagColor` values in
`ui/components/TagColor.kt` (WCAG contrast, (L1 + 0.05) / (L2 + 0.05)), six discs fall below the 3:1 that WCAG 1.4.11
asks of a graphical object such as the check:

| Disc | Luminance | White check now | Black check |
|---|---|---|---|
| ORANGE light `#F57C00` | 0.338 | 2.70 | 7.77 |
| GRAY light `#9E9E9E` | 0.342 | 2.68 | 7.84 |
| RED dark `#EF7A75` | 0.336 | 2.72 | 7.71 |
| CYAN light `#00ACC1` | 0.334 | 2.74 | 7.67 |
| TEAL dark `#4DB6AC` | 0.380 | 2.44 | 8.60 |
| PURPLE dark `#CE93D8` | 0.389 | 2.39 | 8.79 |

Five more get white where black is better but still above 3:1 (RED light 4.23 vs 4.97, GREEN light 3.30 vs 6.36, TEAL
light 4.32 vs 4.86, BLUE light 3.68 vs 5.71, and GRAY dark 3.28 vs 6.41). Choosing the higher of the two puts every disc
of the palette at 4.86:1 or more (the lowest being TEAL light, with black).

`ChordDiagram.kt` L167 already makes this comparison correctly for the keyboard's pressed keys:

```kotlin
val isTowardsDark = (root.luminance() + 0.05f).let { it * it } > (light.luminance() + 0.05f) * (dark.luminance() + 0.05f)
```

## Fix

1. New `ui/components/Contrast.kt` with one pure function:

   ```kotlin
   /**
    * Whether [dark] reads more clearly on [background] than [light] does, by contrast as WCAG measures it: the geometric
    * midpoint of `luminance + 0.05` of the two is where they contrast with it equally, which for black and white is a
    * luminance of about 0.18 rather than the 0.5 the eye would guess.
    */
   internal fun isDarkerMoreLegible(background: Color, light: Color = Color.White, dark: Color = Color.Black): Boolean =
       (background.luminance() + 0.05f).let { it * it } > (light.luminance() + 0.05f) * (dark.luminance() + 0.05f)
   ```
2. `ChordDiagram.pressedKeyColor`: `val isTowardsDark = isDarkerMoreLegible(background = root, light = light, dark = dark)`;
   keep its KDoc's explanation, pointing at the helper.
3. `TagColorButton.tagColorOptions`: `contentColor = if (isDarkerMoreLegible(color)) Color.Black else Color.White`;
   delete `CHECK_LUMINANCE_THRESHOLD`; the KDoc's "whichever the color is lighter or darker than" becomes "whichever
   contrasts with it more".
4. `TagColor.kt`: make the two halves visible to tests (`internal val light`, `internal val dark` instead of
   `private val`), so the palette can be checked.
5. `ui/dialogs/CLAUDE.md`, section "The selection mode's sheets", the last paragraph ("Every row of Manage tags ends in
   the tag's mark (`TagColorButton`)…"): add after "as `ColorChoice` discs" that each disc's check is black or white,
   whichever contrasts with it more.

## Tests

- `ContrastTest`: black and white flip at the crossover of about 0.179 — `isDarkerMoreLegible(Color(0xFF707070))`
  (luminance 0.162) is false and `isDarkerMoreLegible(Color(0xFF7C7C7C))` (0.202) is true — and a disc the old
  threshold got wrong, `isDarkerMoreLegible(Color(0xFFF57C00))`, is true.
- `TagColorTest`: `every color's check contrasts with it by at least three to one` — for every `TagColor` entry and both
  halves, the contrast of the chosen check (computed in the test from `luminance()`) is ≥ 3.0. It fails at d650f3cb0
  for the six discs above.
- The existing `ChordDiagramTest` for `pressedKeyColor` must still pass unchanged.

## Manual check

In a song's Manage tags, open a tag's color popup in the light theme and pick Orange, then Gray and Cyan; switch to the
dark theme and pick Red, Teal, and Purple: each disc's check is black and clearly visible.
