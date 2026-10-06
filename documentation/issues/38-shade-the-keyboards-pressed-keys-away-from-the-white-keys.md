# Shade a keyboard diagram's pressed keys away from the white keys, so that they read in the dark theme

**Challenged:** amended — the luminance-midpoint test picked the white keys as the target in the very theme it was for (the Campfire dark accent's luminance 0.379 is under the midpoint 0.384), giving 1.54:1; and Compose's `lerp` mixes in Oklab, not sRGB, which puts every Material palette's dark half at 2.65:1 with 0.3. The direction is now chosen by contrast ratio and the fraction is 0.4 towards the black keys, 0.3 towards the white ones; numbers re-measured in Oklab for every palette, and the PDF's printed level given.

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChordDiagram.kt`, `presentation/CLAUDE.md`

## Problem

`drawKeyboard` (`components/ChordDiagram.kt`) fills a pressed key other than the root with the accent mixed half way
towards the white keys' color:

```kotlin
val isDarkTheme = backgroundColor.luminance() < lineColor.luminance()
val light = if (isDarkTheme) lineColor else backgroundColor
val dark = if (isDarkTheme) backgroundColor else lineColor
val pressedColor = lerp(rootColor, light, PRESSED_KEY_LIGHTNESS)   // PRESSED_KEY_LIGHTNESS = 0.5f
```

In the dark theme the white keys are `onSurface` (Campfire palette `#E3E1EA`) and the accent is a light orange
(`darkSecondAccent = #FE851E`), so mixing towards the white keys leaves almost nothing to see: measured on the Android
review's screenshot (`16b_keyboard_zoom.png`), a pressed white key `#F5B694` against an unpressed `#E3E1EA` is
**1.35:1**, and the root `#FE851E` itself 1.89:1 (WCAG asks 3:1 of a graphical object). The chord's other notes — the
third, the seventh, a slash chord's bass — are what a keyboard player reads the shape by. In the light theme the same
mix is 2.17:1 (root 5.51:1), weak but legible.

## Fix

Mix the other pressed keys away from whichever of the keyboard's two colors the root is *closer to in contrast*, so that
they move away from the white keys in either theme: towards the black keys by 0.4 where the accent is the lighter one
(a dark theme), towards the white keys by 0.3 where it is the darker one (a light theme, and the PDF):

```kotlin
// Away from whichever key color the accent reads closer to: the black keys where the accent is light (a dark theme),
// the white keys where it is dark, so that the chord's other notes stand off the white keys in either.
val towardsDark = (rootColor.luminance() + 0.05f).let { it * it } > (light.luminance() + 0.05f) * (dark.luminance() + 0.05f)
val pressedColor = lerp(rootColor, if (towardsDark) dark else light, if (towardsDark) PRESSED_KEY_DEEPENING else PRESSED_KEY_PALING)
private const val PRESSED_KEY_DEEPENING = 0.4f
private const val PRESSED_KEY_PALING = 0.3f
```

The test compares the WCAG contrast of the root against each key color (the geometric midpoint of `luminance + 0.05`),
not the arithmetic midpoint of the luminances: that one lies at 0.384 for the Campfire dark theme, a hair above its
accent's 0.379, and would mix towards the white keys exactly as today. Compose's `lerp(Color, Color, Float)`
interpolates in Oklab, which is what the numbers below are measured in (WCAG relative luminance of the result):

| palette, theme | target, fraction | other pressed key vs white key | vs root | today |
|---|---|---|---|---|
| Campfire dark (`#FE851E`, keys `#E3E1EA` / `#15121C`) | black keys, 0.4 | 4.43:1 | 2.35:1 | 1.35:1 |
| Campfire light (`#A04F03`, keys `#FAF8FE` / `#1D1A23`) | white keys, 0.3 | 3.06:1 | 1.80:1 | 2.15:1 |
| Gray, Red … Pink, Orange dark (primary as accent) | black keys, 0.4 | 3.36–3.46:1 | 2.60:1 | 1.13–1.15:1 |
| the same, light | white keys, 0.3 | 3.25–3.29:1 | 1.85–1.87:1 | 2.23–2.25:1 |

(One fraction for both directions does not do: 0.3 leaves the Material dark halves at 2.65:1, 0.4 the light halves at
2.7:1.) The system palette (Android's wallpaper colors) is not a constant; the contrast test picks its direction the
same way. The root stays the accent. The pressed black keys use the same color with their dark outline kept (in the
dark theme the deepened orange is 3.2:1 against the unpressed black keys).

Docs: the KDoc above `drawKeyboard` ("…every other key the chord presses … a paler shade of it, the accent mixed with
the white keys' color…") and `presentation/CLAUDE.md`'s "the rest (a slash chord's bass included) in a paler shade of
it" become "in a shade of it moved away from the white keys (deeper in the dark theme, paler in the light one)". The PDF
draws with fixed colors (`DIAGRAM_ROOT_COLOR = Color(110, 110, 110)`, `Color.Black`, `Color.White` in `PrintRenderer`):
the root's contrast is 5.1:1 against white and 4.1:1 against black, so it is closer to black and its other pressed keys
still mix towards white, now by 0.3: gray 151 (Oklab) instead of 179, which `packPrintRows`' sixteen grays print as
level 9 (153) instead of level 11 (187), the root being level 6 (102) — 2.9:1 against the paper instead of 2.0:1 and
three levels from the root instead of five, a little darker on paper and still distinct. Update the comment above
`DIAGRAM_ROOT_COLOR` ("darker than the paler shade a keyboard's other pressed keys are mixed from it and white" stays
true).

## Tests

None by code: colors are drawing. (If wanted, the choice of direction can be a small pure function
`pressedKeyColor(root, light, dark)` in `ChordDiagram.kt` with a `ChordDiagramTest` asserting the direction for the
Campfire dark and light colors and the PDF's grays — the dark one is the case the first draft of this plan got wrong.)

## Manual check

Choose the keyboard in Settings → Songs, dark theme, open a song: the pressed white keys other than the root are a
clear deep orange-brown against the white keys, the root bright orange; switch to the light theme: the others are a
light orange, the root dark orange. Repeat with a few other colors in Settings, and export a PDF to see its grays.
