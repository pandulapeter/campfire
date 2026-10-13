# Decide the expanded navigation rail from the Songs screen's own column width, so that widening a window never shows, hides, and shows the filter panel again

**Kind:** bug  ·  **Severity:** low–medium  ·  **Platforms:** desktop, web, wide tablets
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChromeKind.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChromeScaffold.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireContent.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChromeKindTest.kt` (new), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`

## Problem

The KDoc of `navigationChromeKind` (`NavigationChromeKind.kt`) promises that the expanded rail is decided from the list screens' own side panel, since "deciding it from anything but the list screens' own side panel would have the panel come, go and come again as a window is widened past both thresholds". But it asks with the default column width:

```kotlin
internal fun navigationChromeKind(windowWidth: Dp) = when {
    !WindowSize.fromWidth(windowWidth).usesNavigationRail -> NavigationChromeKind.BAR
    hasRoomForSidePanel(windowWidth - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH) -> NavigationChromeKind.EXPANDED_RAIL
    else -> NavigationChromeKind.RAIL
}
```

`hasRoomForSidePanel(screenWidth, minColumnWidth = MIN_SONG_COLUMN_WIDTH)` (`components/ListLayout.kt`) defaults to the fresh installation's cards, while the side panel itself has been decided since the onboarding's card details from the user's own cards (`CampfireScreens.kt`):

```kotlin
val listLayout = ListLayout.of(
    settledWidth = settledListWidth,
    ...
    songColumnWidth = minSongColumnWidth(
        details = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault,
        areChordsEnabled = areChordsEnabled,
        isMetronomeEnabled = isMetronomeEnabled,
        isInSetlist = false,
    ),
```

The numbers at 3f8873c06 (`components/ListColumns.kt`, `ListLayout.kt`, `FastScroller.kt`): `SONG_CARD_BASE_WIDTH` 368dp, key and tempo 16dp each, duration 32dp, so `MIN_SONG_COLUMN_WIDTH` is 400dp, and 432dp with Duration shown as well. `hasRoomForSidePanel` wants `width − SIDE_PANEL_WIDTH (320) − FAST_SCROLLER_WIDTH (24) ≥ 2 × column`, and `settledListWidth = windowWidth − settledRailWidth` (about 96dp for the collapsed rail, at least `EXPANDED_NAVIGATION_RAIL_MIN_WIDTH`, 220dp, for the expanded one).

With Duration shown on the song cards (432dp columns), widening a window:

- from about 1304dp: collapsed rail, `1304 − 96 − 344 = 864 = 2 × 432`, the panel appears;
- from 1364dp: `1364 − 220 − 344 = 800 = 2 × 400`, so `navigationChromeKind` switches to the expanded rail; the list is left `1364 − 220 = 1144`dp, which needs 1208 for the panel, and the panel goes;
- from about 1428dp (`1208 + 220`): the panel comes back.

Exactly the come, go, and come again the KDoc rules out, for a user who turned on Duration in the onboarding or in Settings. A narrower column (key or tempo off) does not flicker, it only gets the expanded rail later than it could.

## Fix

Decide both from one column width:

1. `navigationChromeKind(windowWidth: Dp, songColumnWidth: Dp)` — no default, so every caller has to say which — calling `hasRoomForSidePanel(windowWidth - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH, songColumnWidth)`. Update its KDoc to say that the width is the Songs screen's own (`minSongColumnWidth`), the one `ListLayout` decides the panel from.
2. `NavigationChromeScaffold` takes a `songColumnWidth: Dp` parameter and passes it on (`val kind = navigationChromeKind(windowWidth, songColumnWidth)`); its one caller, `CampfireContent.kt`, works it out from `viewModel.userPreferences` the way `CampfireScreens` does.
3. Work the width out once: move the `minSongColumnWidth(...)` call out of `CampfireScreens` into `CampfireContent` (or a small `@Composable` / plain helper both use), and hand the same value to `CampfireScreens` for `ListLayout.of(songColumnWidth = ...)`, so the two decisions can never be made from different widths again. `grep -rn "navigationChromeKind(" presentation tools` must show only the scaffold (and the new test).

Update the expanded rail paragraph of `ui/CLAUDE.md` (it says "`navigationChromeKind`, about 1370dp: it is decided by the window width alone"): it is decided by the window width and the Songs screen's column width, the one its side panel is decided from, about 1370dp with the cards as a fresh installation has them.

## Tests

New `NavigationChromeKindTest.kt` in `presentation/src/commonTest/.../ui/`, pure over `navigationChromeKind`, `hasRoomForSidePanel` and `minSongColumnWidth`. For every combination of `UserPreferences.CardDetails` flags (key, tempo, duration) with chords and the metronome on and off, sweep `windowWidth` from 600dp to 3000dp in 1dp steps and compute the panel as the app does: `hasRoomForSidePanel(windowWidth − railWidth(kind), column)` with `railWidth` 220dp for `EXPANDED_RAIL` and a collapsed rail width of 96dp (declare it in the test as Material's `WideNavigationRail` collapsed width). Assert:

- once the panel has appeared it never disappears again as the width grows (no true → false → true);
- `EXPANDED_RAIL` implies the panel (`hasRoomForSidePanel(windowWidth − 220.dp, column)`);
- the kind never goes from `EXPANDED_RAIL` back to `RAIL` as the width grows.

The first assertion fails at 3f8873c06 for Duration shown, which is the regression guard.

## Manual check

On the desktop app (or the web build in a desktop browser): in Settings, turn on Duration for the song cards. On the Songs screen, in a library whose songs have tags or languages, so that there are filters for the side panel to show (`hasSongFilters`), widen the window slowly from about 1200dp to 1500dp. The panel must appear once and stay, and the rail must expand only while the panel is beside it. Repeat with the cards as a fresh installation has them: the rail still expands at about 1370dp.
