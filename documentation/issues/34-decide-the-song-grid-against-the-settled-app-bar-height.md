# Decide a song page's grid against the height the page has once the app bar has settled, not on every frame of the bar moving

**Kind:** performance (scrolling in short windows)  ·  **Severity:** medium  ·  **Platforms:** all (short windows: a
phone on its side, a 2-in-1 at 200% on a 1080p screen, any window under 480dp tall)
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomePanel.kt` (one
optional parameter; not lane M's `BeatRow.kt`/`MetronomeIcon.kt`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SettledViewportTest.kt` (new),
`presentation/CLAUDE.md`

## Problem

In a short window, the song details app bar leaves as the song is scrolled down and comes back as it is scrolled up.
At 491c4254a (`SongDetailsScreen.kt:401-402` and `:465-466`):

```kotlin
val isCompactHeight = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
val appBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { isCompactHeight })
…
CampfireTopAppBar(
    scrollBehavior = if (isCompactHeight) appBarScrollBehavior else null,
```

The bar is in a `Column` above the pager. Material 3 (1.12.0-alpha03, `AppBar.kt` ~1333-1336) lays the bar out at
`placeable.height + heightOffset`, so the pager's height changes on every frame the bar moves. That means every
frame of a drag that collapses or brings back the bar, and every frame of its snap animation. With `enterAlways`,
that is every time the reader changes scroll direction. The metronome panel in the bar's `bottomContent`
(`SongDetailsScreen.kt:735-741`, an `expandVertically`/`shrinkVertically` over `PANEL_ANIMATION_DURATION`) does the
same while it opens or closes, in windows of any height.

Each page reads that height in its `BoxWithConstraints` (`SongDetailsScreen.kt:1149-1210`):

```kotlin
availableHeight = maxHeight - topPadding - bottomPadding,
…
rowViewportHeight = maxHeight,
```

and `SongLyrics` decides its grid from it (`SongLyrics.kt:382-384`: `availableHeight = availableHeight,
maxRowHeight = availableHeight`). The grid is cached under `SectionGridKey(settledWidth, availableHeight,
maxRowHeight, maxColumnCount, endInset, keepsEndInset)` (`SongLyrics.kt:1532-1539`, `2166-2173`), and
`SectionMeasurements.grid` (`SongLyrics.kt:1968`) searches again whenever the key differs. So every pixel of bar
movement misses the cache and reruns `searchGrid` on the lookahead pass: the inset search and the full-width search,
every column count, `flowLikeAMagazine`, `pageCount`. The approach pass then hits the cache the lookahead filled.
This happens on all three composed pages (`beyondViewportPageCount = 1`). Every constraints change also recomposes
each page's `BoxWithConstraints` content and `SongLyrics` itself, because `onRowsPlaced` captures `maxHeight`.

The existing "burst" handling does not prevent any of this. `rememberContinuousChange(maxWidth, maxHeight,
currentFontScale)` (`SongDetailsScreen.kt:1152-1158`, `1326-1352`) only switches `sectionMotion` from a spring to a
glide during a burst. Its comment names "the short window's title row collapsing over the frames of a scroll and the
metronome panel opening or closing above the pager". It does not coalesce the grid search.

Estimated cost (not measured): 2–6 ms per frame per page on a low-end device for a song of a few dozen sections,
times three pages. Scrolling a song in a short window, or opening the metronome panel, drops frames on exactly the
devices whose windows are short: a phone on its side, or a 2-in-1 at 200% scaling (about 460dp of client height on a
1080p panel).

## Fix

Decide the grid from the height the page had when the bar was last at rest, the way the screen already decides it
from the settled width (`settledWidth` / `extraWidth`). The live height keeps padding the rows, so what the reader
sees during the movement does not change.

1. **When the viewport is settled.** Add a pure helper to `SongDetailsScreen.kt`:

   ```kotlin
   /** Whether the app bar is at rest: Material's own snap leaves it fully expanded or fully collapsed, and below 1% it does not snap at all (`settleAppBar`). */
   internal fun isAppBarSettled(collapsedFraction: Float) = collapsedFraction < 0.01f || collapsedFraction == 1f
   ```

   In `SongDetailsScreen`, build the panel's visibility as a
   `remember { MutableTransitionState(isPanelVisible) }` whose `targetState` follows
   `isMetronomePanelShown && isMetronomeEnabled`. Hand it to `MetronomePanel` through a new optional parameter,
   `visibleState: MutableTransitionState<Boolean>? = null`, which uses `AnimatedVisibility(visibleState = …)` when
   given and today's `visible =` otherwise. Then define
   `val isViewportSettled = { (!isCompactHeight || isAppBarSettled(appBarScrollBehavior.state.collapsedFraction)) && panelState.isIdle }`
   and pass it to every `SongDetailsPage` as `isViewportSettled: () -> Boolean`.
2. **The settled height, in the page.** Inside the page's `BoxWithConstraints`, keep the last settled `maxHeight` in
   a remembered holder: a plain `var` field, not snapshot state. Write it during composition whenever
   `isViewportSettled()` is true. The read subscribes the content to the bar's fraction and the panel's idleness, so
   the content recomposes when the bar or the panel comes to rest, even on a frame where the size did not change.

   ```kotlin
   val settled = remember { SettledHeight(maxHeight) }
   if (isViewportSettled()) settled.height = maxHeight
   val settledHeight = settled.height
   ```

   It starts as the first `maxHeight`, so the first frame is already right (no wait gate). Pass
   `availableHeight = settledHeight - topPadding - bottomPadding`. Keep `rowViewportHeight = maxHeight` and the
   `onRowsPlaced` viewport live.

   As a result, `SectionGridKey` stays the same for the whole movement and nothing is searched again. When the bar or
   the panel comes to rest, the key changes once and one search runs. The rows keep following the live viewport
   through `rowViewportHeight`, which only feeds `arrange`, not the search. Each row is still padded to exactly the
   height on screen, so pedal-only reading still holds: every line is reachable with Up/Down. The step buttons, the
   keys and the pedal do not move the bar (they do not scroll through the nested scroll), so while stepping, the
   viewport is the settled one.
3. Keep `rememberContinuousChange(maxWidth, maxHeight, currentFontScale)` keyed on the **live** height. The rows
   still move with it every frame, and a spring per frame would chase them. A grid that changes at the moment the bar
   settles is still inside the burst's window, so it glides, which is what the existing comment promises for "the
   grid's jumps".
4. Update the comment at `SongDetailsScreen.kt:1152-1158`, and the `availableHeight` / `rowViewportHeight` KDoc in
   `SongLyrics.kt:159-190`. The first should say the grid is decided at the settled height and the rows padded to the
   live one. The second should say `availableHeight` is the height the page has at rest, which may differ from the
   viewport for the frames the bar is moving.

The alternative is to overlay the bar on the pager and translate it, keeping the pager's height constant. The page's
top padding would then have to follow the bar instead, which changes how the fade, the step buttons and the
first-row snapping line up under the bar. It is a bigger visual change for the same saving. Not recommended.

In `presentation/CLAUDE.md`, where the song details screen's short-window app bar and the grid's `availableHeight`
are described, add one sentence: the grid is decided against the page's height with the bar at rest, while the rows
follow the live viewport.

## Tests

`SettledViewportTest` (`presentation/src/commonTest/.../songDetails/`) covers `isAppBarSettled`:

- 0f, 0.005f and 1f are settled;
- 0.01f, 0.5f and 0.999f are not.

If the holder's update rule is written as a pure function (`settledHeight(previous, live, isSettled)`), test that it
keeps `previous` while not settled and takes `live` once settled.

## Manual check

1. On a phone held sideways (or the desktop window under 480dp tall), open a long song in two or more columns. Scroll
   down slowly so the bar leaves, then up so it comes back. The song scrolls smoothly, and the rows still end exactly
   at the bottom edge while the bar moves. Once the bar has settled, any change of columns or pages happens once,
   gliding, rather than flickering through intermediate grids.
2. Pedal or arrow-key through the song with the bar collapsed and with it expanded. Every line is reached, and no
   row's last line is hidden under the bar or below the screen.
3. Open and close the metronome panel from the bar on a wide window and on a short one. The song moves down and up
   smoothly, with no stutter during the panel's animation.
4. Rotate the phone while the bar is collapsed. The grid fits the new height.
