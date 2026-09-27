<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 01 — Only the song on screen follows a pinch; the pages beside it take the settled scale

| | |
|---|---|
| Lane | A |
| Impact | high |
| Confidence | high |
| Platforms | all (worst on Android and on multi-column windows) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | Depends on 02: it uses `viewModel.fontScale` as snapshot state and `SongDetailsPage(fontScale: () -> Float)`. Same pager content lines as 06. 10 makes most of the gain redundant but not wrong. Order: **02 → 01** → 03 → 04 … → 10. |
| Commit message | `Keep pinch-to-zoom smooth on long songs.` |

## Problem
Every frame of a pinch lays out the whole song again on three pages.

- `FontScaleGestures.kt:58-66` reports one new scale per frame. `CampfireViewModel.kt:2064` only clamps it, so every frame produces a new float.
- The value reaches every composed pager page (`SongDetailsScreen.kt:414`, `fontScale = fontScale`). With `beyondViewportPageCount = 1` (`:402`), that is the current page plus one on each side. The pager composes and measures those neighbours even though they are off screen.
- In each page, a new scale changes the keys of every text cache in `SongLyrics`:
  - `SongLyrics.kt:201-208`: every style is `.scaled(fontScale)`.
  - `:212`: `remember(textMeasurer, lyricsStyle, chordStyle, annotationStyle) { SongTextMeasurements(...) }` is rebuilt. Every chord name and lyric fragment is measured again, with `rememberTextMeasurer(cacheSize = 0)`, so each is a full paragraph layout.
  - `:195`: `remember(sections, fontScale, density, foldedSections) { SectionMeasurements(...) }` is rebuilt. On multi-column windows, every section's `maxIntrinsicHeight` is asked again at every candidate width (`:1258-1273`, and `SectionGrid.kt:192` for the horizontal flow).
  - `:1665-1673`: every `SongLineWithChords` re-pads and its `Text` lays out again.
  - `:955`: every `TabRows` is rebuilt.

Per frame, the cost is (fragments + chords + lines + tab rows) × (1, or k + 1 widths on a multi-column window) × **3 pages**. Two of those three pages are not visible.

## Fix
1. **ViewModel** (after 02): keep a settled value next to the live one.
   ```kotlin
   private val settledFontScaleState = mutableFloatStateOf(DEFAULT_FONT_SCALE)
   /** [fontScale] once it has held still for a moment: what the songs that are not on screen are laid out at. */
   val settledFontScale: Float get() = settledFontScaleState.floatValue
   ```
   Feed it from a collector in `init`: `snapshotFlow { fontScale }.debounce(FONT_SCALE_SETTLE_MILLIS).collect { settledFontScaleState.floatValue = it }`, with `FONT_SCALE_SETTLE_MILLIS = 200L`. Also set it directly wherever the stored preference is applied, and in `adjustFontScale` and `zoomSongText`: discrete steps are not continuous changes, so the neighbours can follow at once.
2. **Pager content** (`SongDetailsScreen.kt:403-437`). `currentPage` and `targetPage` are already read there, and both change only discretely:
   ```kotlin
   val isFollowingGesture = page == pagerState.currentPage || page == pagerState.targetPage
   SongDetailsPage(
       fontScale = if (isFollowingGesture) ({ viewModel.fontScale }) else ({ viewModel.settledFontScale }),
       …
   )
   ```
   The two lambdas are memoized by strong skipping, since they capture only `viewModel`. `SongDetailsPage` therefore keeps skipping, and only the visible page's content scope reads the live value.
3. **Rounding.** Round the scale to whole percent in `setFontScale`: `((clamped * 100).roundToInt() / 100f)`. This is safe for the stepper and for stored values:
   - The label is already `"${(fontScale * 100).roundToInt()}%"` (`SongDisplayControls.kt:236`), so it never shows anything the stored value does not.
   - Stepper values are multiples of 0.1, and both bounds (0.5, 2.5) are whole percentages, so they survive unchanged.
   - `adjustFontScale` keeps its `FONT_SCALE_STEP_TOLERANCE`.
   - The damped pinch (exponent 0.4) often moves less than 1% per frame when it is slow and precise. Those frames then set an equal value, which snapshot state ignores, so they cause no recomposition and no relayout.
   - A coarser grid (2%) is **not** used, because it would make the label skip odd percentages.
4. **What must not change:**
   - The visible page still re-wraps live while pinching; that is the documented intent in `FontScaleGestures.kt` ("readable while it is being resized").
   - The neighbours catch up about 200 ms after the gesture stops, before a swipe can reveal them. A swipe that starts earlier makes the page the `targetPage`, which switches it to the live value.

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug`.
- Android `.debug` build on a mid-range device or emulator, with a setlist of three long songs, opened on the middle one. Pinch slowly for 3 s while running `adb shell dumpsys gfxinfo com.pandulapeter.campfire.debug framestats` (reset first). The janky-frame count and the 90th/99th percentile frame time should drop roughly threefold compared with before.
- In Layout Inspector, the neighbour pages' `SongLyrics` should recompose once after the pinch ends, not per frame.
- Pinch, then swipe right away. The next song appears at the new size, possibly re-wrapping once as it becomes the target.
- `presentation/CLAUDE.md`, `SongDisplayControls.kt / FontScaleGestures.kt` entry: add that only the page on screen follows a pinch, and that the scale is kept in whole percent.
