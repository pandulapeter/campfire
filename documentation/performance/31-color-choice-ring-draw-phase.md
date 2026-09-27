<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 31 — Draw the colour choice's selection ring in the draw phase

| | |
|---|---|
| Lane | D |
| Impact | low (two swatches recompose per frame for the ring's fade; it coincides with the theme fade) |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ColorChoice.kt |
| Depends on / conflicts with | — (26 changes the theme fade that runs at the same moment, but not this file) |
| Commit message | `Draw the selected color's ring without recomposing the swatch for every frame of its fade.` |

## Problem
`components/ColorChoice.kt:92-100`:
```kotlin
val ringAlpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0f)
Box(
    modifier = modifier
        .size(...)
        .border(
            width = SWATCH_RING_WIDTH,
            brush = Brush.linearGradient(listOf(option.color.copy(alpha = ringAlpha), option.secondColor.copy(alpha = ringAlpha))),
            shape = CircleShape,
        ),
```
- `ringAlpha` is read in composition. On every frame of the ring's fade, both the swatch losing the selection and the one gaining it recompose.
- Each of those recompositions allocates a new `Brush`, and `Modifier.border` rebuilds its cached outline and brush.
- This runs on Settings → General and the welcome sheet, in exactly the frames where the theme cross-fade is also recomposing the screen (plans 25 and 26).

## Fix
Keep the `State` rather than delegating it, and read it only while drawing. Cache the gradient per colour pair:
```kotlin
val ringAlpha = animateFloatAsState(targetValue = if (isSelected) 1f else 0f)
Box(
    modifier = modifier
        .size(SWATCH_SIZE + (SWATCH_RING_GAP + SWATCH_RING_WIDTH) * 2)
        .drawWithCache {
            val stroke = SWATCH_RING_WIDTH.toPx()
            val brush = Brush.linearGradient(listOf(option.color, option.secondColor))
            onDrawBehind {
                val alpha = ringAlpha.value
                if (alpha > 0f) drawCircle(brush = brush, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke), alpha = alpha)
            }
        },
    contentAlignment = Alignment.Center,
)
```
- `drawWithCache` rebuilds its cache when the lambda's captured `option` changes, because the modifier is recreated with it. That keeps the ring's colours following a theme change.
- `linearGradient` with default start and end spans the drawn bounds diagonally, as the border's does.
- The stroke sits inside the bounds, exactly where `border` puts it (inset by half the width).

What must NOT change:
- The ring is always laid out, so the discs never move as the selection does.
- The ring keeps the option's own colour, a two-colour gradient for the app's own palette.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run`.
- Compare screenshots of Settings → General before and after, in the light and dark modes, with the Campfire palette selected (gradient ring) and with a plain one: they are identical.
- Tap between discs: the ring still fades.
- With a temporary `SideEffect` counter in `ColorChoiceSwatch`, a tap recomposes each affected swatch once or twice rather than once per frame. Ignore the theme fade's own recompositions, or test with plan 25/26 in place.
