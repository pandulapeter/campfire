<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 28 — Stop a window resize from recomposing the top-level screens on every frame

| | |
|---|---|
| Lane | D |
| Impact | low-medium: desktop drag-resize, the web build in a resized browser window, Android multi-window and foldables |
| Confidence | medium (measure first, see step 0) |
| Platforms | all; mostly desktop and web |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsLayout.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Controls.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | Any plan in another lane that edits the top of `SongsScreen`, `SetlistsScreen` or `SettingsScreen` (their signatures change). Also 25 and 32 in `CampfireApp.kt`, in different blocks. |
| Commit message | `Hand the top level screens the layout decisions a width makes rather than the width itself.` |

## Problem
`NavigationChromeScaffold` (`CampfireApp.kt:754-770`) subcomposes the screens on every measure with the window's current width:
```kotlin
val windowWidth = constraints.maxWidth.toDp()
...
val contentPlaceable = subcompose(ChromeSlot.CONTENT) { content(windowWidth, windowSize, kind, chromeSize) }
```
While a window is being resized, `windowWidth` changes every frame (by a pixel's worth of dp). That leads to the following:
- `CampfireScreens` (`:456`) recomposes.
- It rebuilds `entryProvider { … }` (`:556`). The entry content lambdas are created inside a non-composable builder, so strong skipping does not memoize them, and every visible entry runs again.
- It hands each top-level screen a new `settledWidth = windowWidth - chromeSize.settledRailWidth` (`:483`).
- The screen roots therefore recompose every frame, on top of the relayout the resize needs anyway:
  - `SongsScreen` (`SongsScreen.kt:114-134`, with about ten `collectAsState`s and the grid setup);
  - `SetlistsScreen` (`:116-123`);
  - `SettingsScreen` (`:179-285`), whose `SettingsTabPager` composes all four pages.

Yet what those screens *decide* from the width is discrete:
- **Songs:** `hasRoomForSidePanel(settledWidth)` and `songListColumnCount(settledWidth, contentPadding, isSidePanelVisible)` (`Controls.kt:~20-45`).
- **Setlists:** the same column count.
- **Settings:** `isWide = pageWidth > SETTINGS_TAB_ROW_MAX_WIDTH + SETTINGS_CATEGORY_PANE_WIDTH` (`SettingsScreen.kt:218-229`), and each `SettingsPage`'s `(settledWidth / MIN_COLUMN_WIDTH).toInt()` (`SettingsLayout.kt:167`).

## Fix
0. **Measure before changing anything.** Resize the desktop window by dragging for 5 s, with composition tracing (`androidx.compose.runtime:runtime-tracing`), or with temporary `SideEffect` counters at the top of `CampfireScreens`, `SongsScreen` and `SettingsScreen`. If the roots are not recomposing per frame (for example if the pixel-to-dp rounding keeps the values equal), or if the frames are dominated by layout, drop this plan.
1. **In `CampfireScreens`, compute each screen's discrete layout from the settled width.** Pass that instead of the width. These are small data classes, so strong skipping sees equal values while nothing flips:
   ```kotlin
   @Immutable data class ListLayout(val hasRoomForSidePanel: Boolean, val columnCount: Int, val columnCountBesideSidePanel: Int)
   @Immutable data class SettingsWidthLayout(val isWide: Boolean, val sectionColumns: Int, val sectionColumnsBesidePane: Int)
   ```
   Build them with the existing pure helpers (`hasRoomForSidePanel`, `columnCountForWidth`, the settings thresholds). Move the width arithmetic from `songListColumnCount` into a non-composable function that takes the start and end padding and the `LayoutDirection` explicitly. `CampfireScreens` already has both `shellContentPadding` and `layoutDirection`.
2. **Screens take the layout rather than `settledWidth`:**
   - `SongsScreen`: `isSidePanelVisible = hasSongFilters && layout.hasRoomForSidePanel`, and `columnCount = if (isSidePanelVisible) layout.columnCountBesideSidePanel else layout.columnCount`.
   - `SetlistsScreen`: the same, without the panel.
   - `SettingsScreen` and `SettingsPage`: `isWide` and the section column count.

   Keep "decided from the *settled* width, not the animating one". That rule (see the `NavigationChromeSize` KDoc) is why these are computed from `settledRailWidth`.
3. **Leave `SongDetailsScreen` on `settledWidth`.** `SongLyrics` uses the continuous width inside its layout (`SongLyrics.kt:1195`, `extraWidth`), and the details screen belongs to another lane. Say so in the commit's KDoc so the asymmetry reads as deliberate.
4. **Optional, only if step 0 shows `CampfireScreens` itself as significant:** hoist `navigationMetadata` into a `remember(viewModel.navigationGeneration)`. It is a new map on every composition today.

## Verification
- `./gradlew :presentation:desktopTest`, `:app:desktop:run`, `:app:web:wasmJsBrowserDevelopmentRun`, `:app:android:assembleDebug`.
- With the counters from step 0, drag-resize: the three screen roots recompose only when a column count, the side panel, or the settings layout flips.
- Visual check across the breakpoints:
  - the bar to the rail at 600dp, and the rail to the expanded rail at about 1290dp;
  - the side panel appearing;
  - the settings category pane appearing;
  - the settings sections going side by side.
  
  None of them changes where it happens.
- Update `presentation/CLAUDE.md` where it says the screens decide "from the settled width" (the `ui/CampfireApp.kt` paragraph and `hasRoomForSidePanel`): the decisions are now made once, in `CampfireScreens`, and handed down.
