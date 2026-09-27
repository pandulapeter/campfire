<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 03 — The text size label does not cross-fade on every percent of a pinch

| | |
|---|---|
| Lane | A |
| Impact | low–medium |
| Confidence | high |
| Platforms | all (the inline stepper appears wherever the bar has room; the sheet version appears while the sheet is open) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt |
| Depends on / conflicts with | Touches `FontScaleControls`, which 02 also edits (02 changes only its callers). Land after 02 and 01. Order: 02 → 01 → **03** → 04 … |
| Commit message | `Stop the text size label from flickering during a pinch.` |

## Problem
`SongDisplayControls.kt:338-355`: `StepperValue` shows its value through

```kotlin
AnimatedContent(
    modifier = Modifier.fillMaxHeight(),
    targetState = value,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { currentValue -> Text(...) }
```

The font scale label is `fontScaleLabel(fontScale)` = `"${(fontScale * 100).roundToInt()}%"` (`:236`). A pinch changes it about once per frame, and every change starts another pair of fade transitions and adds another child to the `AnimatedContent`. With the default spring fade, a dozen or so `Text` children are composed, measured and drawn on top of each other. That costs extra work every frame of the gesture, and the label reads as a blur instead of a number. The cross-fade exists for taps on the stepper and on the transposition stepper, where the value changes once.

## Fix
1. `Stepper` and `StepperValue` gain a `valueKey: Any` parameter, defaulting to `value`. `AnimatedContent` uses it as its `contentKey`. When two targets share a key, `AnimatedContent` updates the visible content in place instead of transitioning:
   ```kotlin
   AnimatedContent(
       targetState = value,
       contentKey = { valueKey },
       …
   )
   ```
   Because `contentKey` receives the state, keep the key alongside it: pass `targetState = StepperLabel(value, valueKey)` (a small private data class) and use `contentKey = { it.key }`, `Text(text = it.value)`.
2. `FontScaleControls` passes `valueKey = (fontScale / CampfireViewModel.FONT_SCALE_STEP).roundToInt()`, the stepper step the value is nearest to:
   - A tap on − or +, a keyboard zoom, and a reset each move to another step, so they still cross-fade.
   - A pinch cross-fades at most once per 10%, and updates the number in place in between.
3. `TranspositionControls` and `TextTranspositionControls` keep the default key (`value`), so nothing changes for them.
4. **What must not change:**
   - The `changedProgress` colour logic.
   - `VALUE_MIN_WIDTH`, which already fits every percentage (`rememberCompactSteppersWidth` measures `MIN`/`MAX`), so an in-place update never changes the pill's width.

## Verification
- Run `./gradlew :presentation:desktopTest :app:desktop:run`.
- On the desktop, open a song on a window wide enough for inline steppers. Ctrl + scroll slowly: the number counts smoothly without ghosting. Click + and −: the value still cross-fades.
- Layout Inspector (Android) during a pinch: the `AnimatedContent` in `StepperValue` holds a single child.
