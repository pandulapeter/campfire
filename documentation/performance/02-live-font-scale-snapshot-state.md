<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 02 — Keep the live font scale as snapshot state and read it where the text is drawn

| | |
|---|---|
| Lane | A |
| Impact | medium |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | Land first among the font scale plans: 01, 03, 04 and 10 build on the API introduced here. It touches the same lines as 01 (pager content in `SongDetailsScreen`), 03 (`FontScaleControls`), 06 and 09 (`SongDetailsScreen`). Order: **02 → 01 → 03 → 04 → 05 → 08 → 07 → 06 → 09 → 10**. |
| Commit message | `Keep the rest of the song screen still while its text size changes.` |

## Problem
The font scale is a coroutine flow collected at the root of the screen.

- `CampfireViewModel.kt:759-762`:
  ```kotlin
  private val pendingFontScale = MutableStateFlow<Float?>(null)
  val fontScale = combine(userPreferences, pendingFontScale) { userPreferences, pendingFontScale ->
      pendingFontScale ?: userPreferences?.fontScale ?: DEFAULT_FONT_SCALE
  }.asState(DEFAULT_FONT_SCALE)
  ```
- `SongDetailsScreen.kt:143` reads it with `val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()` at the top of `SongDetailsScreen`.

During a pinch, `FontScaleGestures.kt:58-66` calls `setFontScale` once per frame. Each call then does the following:

1. It goes through `MutableStateFlow`, the `combine` coroutine, `stateIn`, and the `collectAsStateWithLifecycle` collector. Each of these is a coroutine hop, so the value that reaches the composition can be a frame behind the fingers.
2. It recomposes the whole `SongDetailsScreen` on every frame:
   - the `CampfireTopAppBar`, whose `actions` lambda captures `fontScale` (`SongDetailsScreen.kt:325-331`);
   - both `AnimatedVisibility`s;
   - the `rememberCompactSteppersWidth` call site;
   - the pager's content lambda, which captures `fontScale` (`SongDetailsScreen.kt:414`), so all three composed pages run again.

The only things that actually need the value are each page's `SongLyrics`, the text-size stepper, and the gesture.

There is also a latent correctness quirk. Once `pendingFontScale` is non-null it is never cleared, so from the first pinch of a session any later change of `userPreferences.fontScale` is ignored for the rest of the session. That includes preferences being read again, and anything else that writes the preference.

## Fix
1. **ViewModel.** Replace the flow with snapshot state and keep the debounced save.
   ```kotlin
   private val liveFontScale = mutableFloatStateOf(DEFAULT_FONT_SCALE)
   /** Snapshot state, so that it is read where the text is laid out: a pinch changes it on every frame. */
   val fontScale: Float get() = liveFontScale.floatValue
   /** The value set on this device and not yet written to the preferences. */
   private val unsavedFontScale = MutableStateFlow<Float?>(null)

   fun setFontScale(value: Float) {
       val clamped = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
       if (clamped == liveFontScale.floatValue) return
       liveFontScale.floatValue = clamped
       unsavedFontScale.value = clamped
   }
   ```
   In `init`:
   - Replace the existing save collector (`CampfireViewModel.kt:945-949`) with one that saves and then clears the pending value, but only if nothing newer arrived meanwhile:
     ```kotlin
     unsavedFontScale.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { value ->
         userPreferences.value?.let { saveUserPreferences(it.copy(fontScale = value)) }
         unsavedFontScale.compareAndSet(value, null)
     }
     ```
   - Add a collector that lets the stored value win whenever nothing unsaved is pending. This covers the first read at launch, a preferences re-read, a restore, and anything else that writes the preference:
     ```kotlin
     userPreferences.filterNotNull().map { it.fontScale }.distinctUntilChanged().collect { stored ->
         if (unsavedFontScale.value == null) liveFontScale.floatValue = stored
     }
     ```
     The echo of our own save equals the live value, so it is a no-op.
   - `adjustFontScale` (`CampfireViewModel.kt:2071-2075`) reads `liveFontScale.floatValue` instead of `fontScale.value`. `zoomSongText` is unchanged.
2. **`SongDetailsScreen`.**
   - Delete the root read (`:143`) and `currentFontScale` (`:371`).
   - The gesture gets `fontScale = { viewModel.fontScale }`.
   - `SongDetailsPage` takes `fontScale: () -> Float` and calls it inside its `AnimatedContent` content, right where `SongLyrics(fontScale = fontScale())` is built (`:638`). A change then invalidates only that page's content scope, not the screen and not the pager's content lambda.
   - The inline stepper reads the value inside a composable of its own, so that only it recomposes:
     ```kotlin
     @Composable
     private fun LiveFontScaleControls(viewModel: CampfireViewModel, modifier: Modifier) = FontScaleControls(
         modifier = modifier, isCompact = true, fontScale = viewModel.fontScale,
         onFontScaleAdjusted = viewModel::adjustFontScale,
         onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) },
     )
     ```
3. **`SongDisplayControls.kt:89`**: delete the `collectAsStateWithLifecycle` and pass `viewModel.fontScale` straight into `FontScaleControls` inside the `trailingContent` lambda, so the read happens in that lambda's scope.
4. **What must not change:**
   - The editor keeps reading `userPreferences?.fontScale` (`SongEditorScreen.kt:312`). Its preview follows the saved value, as it does today.
   - The save debounce (`FONT_SCALE_SAVE_DELAY_MILLIS`) stays at 500 ms.
   - The clamping bounds stay the same.
   - The gesture still builds on its own pending value (`FontScaleGestures.kt:49-57`).

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.
- Android `.debug` build, a long song, Layout Inspector with recomposition counts on. Pinch for about 2 s. Check that:
  - `CampfireTopAppBar`, `SongPagerControls` and the pager's content lambda no longer count up once per frame;
  - only `SongLyrics` of the pages and the text-size stepper do.
- Correctness:
  - Pinch, wait more than 0.5 s, kill the app, reopen. The size is kept.
  - Ctrl + plus on the desktop immediately after a pinch steps from the pinched value.
- Update `presentation/CLAUDE.md` (the `SongDisplayControls.kt / FontScaleGestures.kt` entry): the font scale is snapshot state on the view model, read where it is used, and the stored value wins whenever nothing unsaved is pending.
