<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Deferred by the user on 2026-09-27: not executed until they have tried 01–04 on a phone.
# 10 — DECISION NEEDED: scale the song visually during a pinch, and re-wrap it once when the fingers lift

| | |
|---|---|
| Lane | A |
| Impact | high: no text layout at all during a pinch, one relayout at the end |
| Confidence | medium (the mechanism is simple; the feel needs to be tried on a device) |
| Platforms | all (pinch on touch; Ctrl / Cmd + wheel and trackpad pinch on the desktop and the web) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/FontScaleGestures.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | Last of the font scale plans: builds on 02 (snapshot state), 01 (settled scale), 03 (label key) and 04 (`animatesSections`). Order: 02 → 01 → 03 → 04 → 05 → 08 → 07 → 06 → 09 → **10**. |
| Commit message | `Zoom a song smoothly during a pinch and re-wrap it once the fingers lift.` |

**This is a UX change and needs the user's approval before it is executed.** Today the lyrics re-wrap live while the fingers move. With this plan, the page grows or shrinks as a picture during the gesture, and the lines re-wrap to the new width only once the fingers lift, or once the wheel stops for about 150 ms. The keyboard shortcuts and the stepper still apply their steps directly. `FontScaleGestures.kt`'s KDoc ("the text has to be readable while it is being resized") is still honoured, because a scaled layer is readable. What changes is that a line longer than the screen runs past its edge until the release.

## Problem
Even with 01, every frame of a pinch lays out the page on screen again, completely: every styled text cache is rebuilt (`SongLyrics.kt:195`, `:201-212`, `:955`, `:1665-1673`), and on multi-column windows every section is measured at every candidate width. A long song on a mid-range Android device cannot do that within a frame, so a pinch stays janky however much the other plans trim around it.

## Fix
1. **`FontScaleGestures.kt`.** Separate what the gesture shows from what it commits:
   ```kotlin
   internal fun Modifier.fontScaleGestures(
       fontScale: () -> Float,
       onGestureScale: (scale: Float, pivot: Offset) -> Unit,    // the scale the gesture is at, relative to fontScale()
       onGestureEnd: () -> Unit,                                  // commit
   )
   ```
   - A pinch reports `onGestureScale(target / start.fontScale, centroid)` on every event. This is cheap because it is only written into snapshot state. When the last finger lifts, it calls `onGestureEnd()`.
   - The wheel accumulates the same way and commits after `WHEEL_COMMIT_DELAY_MILLIS = 150` without a notch, using the existing frame-request channel plus a `delay`.
   - The frame conflation stays in place for the state writes.
2. **ViewModel** (after 02): add the visual ratio next to the live scale.
   ```kotlin
   private val gestureScaleState = mutableFloatStateOf(1f)
   val gestureScale: Float get() = gestureScaleState.floatValue
   var gesturePivot by mutableStateOf(Offset.Unspecified); private set
   fun setGestureScale(ratio: Float, pivot: Offset) { gestureScaleState.floatValue = ratio.coerceIn(MIN_FONT_SCALE / fontScale, MAX_FONT_SCALE / fontScale); gesturePivot = pivot }
   fun commitGestureScale() = Snapshot.withMutableSnapshot { setFontScale(fontScale * gestureScale); gestureScaleState.floatValue = 1f }
   ```
   `Snapshot.withMutableSnapshot` makes the new scale and the reset ratio land in the same frame, so the text never shows both scales at once. The text-size label shows `fontScale * gestureScale` (read inside the stepper's own scope, see 02), so it still counts during the gesture.
3. **`SongDetailsScreen`**: apply the ratio to the current page only, as a layer **inside** the vertical scroll. The display list below the viewport is then revealed when zooming out, instead of blank margins appearing:
   ```kotlin
   .verticalScroll(state = scrollState, flingBehavior = flingBehavior)
   .graphicsLayer {
       val ratio = if (isCurrentPage) viewModel.gestureScale else 1f
       scaleX = ratio; scaleY = ratio
       val pivot = viewModel.gesturePivot
       transformOrigin = if (pivot.isSpecified) TransformOrigin(pivot.x / size.width, (scrollState.value + pivot.y) / size.height) else TransformOrigin.Center
   }
   ```
   Every read is inside the layer block, so nothing recomposes or relayouts during the gesture. The pivot has to be converted from the pager's coordinates into the content's: subtract the page's top padding and horizontal padding.
4. **Interplay with the other plans:**
   - **01** keeps its value for the wheel's commits and the keyboard's steps. During a pinch it becomes moot, because nothing is laid out until the release. Keep it.
   - **02** is required: the ratio is snapshot state read in draw, and the committed scale is what `SongLyrics` reads.
   - **03**: the label changes by 1% per frame again during a pinch, and the bucket key keeps it from cross-fading. Keep it.
   - **04**: the commit is a single discrete change. Without care, `animateBounds` would spring every section from its old, unscaled place to the new layout on release. Pass `animatesSections = false` for the frame of a commit. Simplest: have `SongDetailsPage` treat a change of `fontScale` that coincides with `gestureScale` going back to 1 as continuous, by feeding `rememberContinuousChange` a commit counter from the view model. The page then snaps from the scaled picture to the re-wrapped text instead of animating.
   - **After the release** the page snaps once from the scaled image to the real layout. The two differ where lines re-wrap. This is the trade the user has to accept.
5. **What must not change:**
   - One-finger scrolling, and the pager's swipe, are untouched. Only a gesture with two or more pointers is consumed (`FontScaleGestures.kt:94-95`).
   - The stored value is still debounced (02).

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug :app:desktop:run :app:web:wasmJsBrowserDevelopmentRun`.
- Android, a long song in two columns on a tablet. Pinch for 3 s while `dumpsys gfxinfo … framestats` runs: no janky frames during the gesture, and one long frame at the release.
- Zoom out by pinching. The lines below the old bottom edge appear during the gesture, with no blank band.
- Desktop and web: Ctrl + wheel for several notches. The page scales smoothly, then re-wraps about 150 ms after the last notch. Ctrl + plus still steps immediately.
- `presentation/CLAUDE.md`, `SongDisplayControls.kt / FontScaleGestures.kt` entry: describe the visual scale during a gesture and the commit on release. Also update `FontScaleGestures.kt`'s KDoc (the per-frame "lays the whole song out again" paragraph).
