# Keep the navigation bar's and rail's labels on one line at large text, shrinking them rather than breaking a word

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all (windows narrow enough for the bar or the plain rail)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChrome.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (the navigation chrome
paragraph, if it describes the labels)

## Problem

With the Tuner the bar holds five items, 72dp each on a 360dp phone. The plain bar's and rail's labels are bare
`Text`s (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChrome.kt:133` and
`:155` at b5c8ed3b5):

```kotlin
                        label = { Text(stringResource(destination.label)) },
```

The expanded rail already keeps its label on one line (`maxLines = 1, overflow = TextOverflow.Ellipsis`, line 111),
these two do not. "Beállítások" (`settings` in Hungarian) is ~68dp in `labelMedium` at 1.0x; at 1.3x it is ~88dp, and a
single word that does not fit is broken between letters: "Beállítá" / "sok". "Metronóm" is at the edge at 1.3x too.

## Fix

Options:

- A. `maxLines = 1, overflow = TextOverflow.Ellipsis`, as the expanded rail does. Simple, but at 1.3x the Settings tab
  reads "Beállítá…", which is not a word a reader can complete.
- **B. (recommended) Auto-size the label down to a floor, then ellipsize.** Material 3's `Text` takes `autoSize:
  TextAutoSize?` in `org.jetbrains.compose.material3` 1.12.0-alpha03 (`material3/Text.kt`), and
  `TextAutoSize.StepBased(minFontSize, maxFontSize, stepSize)` is in Foundation 1.12. Only the label that does not fit
  shrinks, and at 1.3x it shrinks by about a fifth — still larger than at 1.0x, which is what the user asked for.

```kotlin
/**
 * A navigation item's label, on one line however large the text: one that does not fit its item is made smaller
 * rather than broken, since a word broken between its letters - five items share a phone's width - reads as two, and
 * past the floor it is cut short.
 */
@Composable
private fun NavigationItemLabel(text: String) {
    val fontSize = LocalTextStyle.current.fontSize
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = fontSize * MIN_NAVIGATION_LABEL_SCALE,
            // The style's own size, or the auto size would grow a short label to fill its item.
            maxFontSize = fontSize,
        ),
    )
}

private const val MIN_NAVIGATION_LABEL_SCALE = 0.75f
```

and `label = { NavigationItemLabel(stringResource(destination.label)) }` in both the `NavigationRail` and the
`NavigationBar` branch. `LocalTextStyle` is the one the item provides (`labelMedium`, scaled by the app's interface
scale), so the cap follows both the system font scale and the app's own. The expanded rail keeps its fixed-width label.
`maxFontSize` must stay set: `StepBased`'s default is 112.sp.

## Tests

None: a Composable's text layout.

## Manual check

- Android, Hungarian, font size 1.3x and the maximum, 360dp wide phone portrait: all five labels on one line;
  "Beállítások" a little smaller than the others at 1.3x, cut with an ellipsis only at the very largest size; the
  other labels at their normal (scaled) size.
- English at 1.0x: unchanged.
- A window between 600 and 840dp (plain rail): the same.
