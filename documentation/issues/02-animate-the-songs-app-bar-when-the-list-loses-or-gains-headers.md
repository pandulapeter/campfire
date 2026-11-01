# Animate the songs app bar and list inset when the list loses or gains its section headers

**Kind:** bug (UI rule)  ·  **Severity:** medium-low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/appBar/SearchBarMotion.kt`
(only if `animateAppBarReveal` must learn to snap, see Fix 2),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/KeepCardsInPlaceUnderTick.kt`
(only if Fix 3 needs it),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongHeaderlessBarTest.kt` (new)
**Challenged:** amended — Fix 3's compensation is required, not conditional: `underAppBar` moves the list's whole
viewport by the inset (`AppBarOverlap.kt`), and `KeepCardsInPlaceUnderTick` holds a card's offset inside the viewport,
so the card travels with the inset on screen. Fix 2 names the smallest way to snap (`animateFloatAsState` with a
`snap()` spec). The test now also checks the guard the instant branch was written for (no collapsed first row kept
as a scroll offset), on a sort change and on the first song arriving in an empty library after loading.

## Problem

The songs app bar fills in, and the list moves down under it, whenever the list has no section header
(`animateAppBarReveal`'s `isShownWithoutSearch`), as for a search's ranked results. Until the search is first opened,
`SongsScreen` reads that state *without* the animation (`SongsScreen.kt`):

```kotlin
// Loading can replace a headerless placeholder with the first section. Before search has ever opened,
// lay that section out fully expanded on its first frame so the grid cannot retain a collapsed first row.
var hasOpenedSearch by remember { mutableStateOf(isSearchOpen) }
SideEffect { if (isSearchOpen) hasOpenedSearch = true }
val isHeaderless = placeholder != null || shownSongGroups.groups.none { it.header != null }
val appBarProgress = remember(appBarReveal, isSearchOpen, hasOpenedSearch, isHeaderless) {
    { if (isSearchOpen || hasOpenedSearch) appBarReveal.value else if (isHeaderless) 1f else 0f }
}
```

The instant branch exists for one case only: the loading placeholder (headerless) giving way to the first section,
which must be right on its first frame (root `CLAUDE.md`: data arriving is fixed at the source, never animated over).
Before 5506fd7d4 a loaded list always had headers, so nothing else reached it. Shuffle (5506fd7d4) is one headerless
section (`SongSection.header` is now nullable), so these user actions now flip `appBarProgress` between 0 and 1 in one
frame, moving the bar's fill, the list inset (`underAppBar { 1f - appBarOverlap().coverage }`), and the pinned header
height at once:

- picking Shuffle in `SongSortMenu`, or leaving it for another sort, before the search was ever opened this session;
- starting selection mode under Shuffle: the mode starts with the song it was started from ticked, so
  `rememberSongGroupsWithSelection` → `withSelectionLeading` leads the list with a `SongGroupHeader.Selection` group,
  the list is no longer headerless, and the bar empties at once; ending the mode fills it at once.

Root `CLAUDE.md`: "Every user-caused change is animated." After the search has been opened once the same changes do
animate, through `appBarReveal`, so the behaviour also depends on an unrelated earlier action.

## Fix

1. Keep the instant value only until the list first leaves the loading placeholder; after that, always read
   `appBarReveal.value`. Replace `hasOpenedSearch` with a latch that turns on once the screen has shown real content
   (`placeholder == null` or a non-loading placeholder, with `hasLoadedLibrary`; set in a `SideEffect`, as
   `hasOpenedSearch` is now), or keep `hasOpenedSearch` and add the latch beside it; the expression becomes
   `if (isSearchOpen || hasOpenedSearch || hasShownList) appBarReveal.value else if (isHeaderless) 1f else 0f`.
   Keep the comment, reworded to say the instant value covers only the loading → first content step.
2. The spring must not start from a stale value when the latch turns on. `animateAppBarReveal`
   (`components/appBar/SearchBarMotion.kt`) is an `animateFloatAsState` that starts at its first target: during
   loading that is 1 (the placeholder is headerless) and an alphabetical list then targets 0, so at the latch it is
   mid-flight from 1 to 0 while the instant branch had shown 0. Make the reveal snap to its target while the instant
   branch is in use. The smallest way: give `animateAppBarReveal` an `isSnapped: Boolean` parameter (the setlists
   screen passes `false` and is unchanged) and pass `animationSpec = if (isSnapped) snap() else searchTravelSpec()` to
   its `animateFloatAsState`, which animates each new target with the spec current when it changes: while loading the
   value jumps to each target, and the first change after the latch travels. (An `Animatable` with `snapTo` does the
   same with more code.) Either way the first frame after loading is what the
   instant branch drew, and only later changes travel on `searchTravelSpec`.
3. The list must not jump under the finger when selection mode starts under Shuffle: the card whose menu started it
   stays where it is on screen while the bar empties. `KeepCardsInPlaceUnderTick` keeps the first visible card
   outside the Selection group at its offset **inside the list's viewport** (`requestScrollToItem(index, -offset)`),
   but `underAppBar` places the whole viewport `LIST_APP_BAR_HEIGHT × reveal` down (`AppBarOverlap.kt`), so as the
   inset travels the held card travels with it on screen: it does not hold by itself. When a tick also changes whether
   the list has headers, add the inset's travel to the held offset the way `KeepCardsInPlaceAcrossSearch` adds
   `listShift` (`SEARCH_LIST_SHIFT × (coverage - targetCoverage)`), narrated by the same `ListAnchor` on
   `searchTravelSpec()`; give `KeepCardsInPlaceUnderTick` what it needs for that (`appBarOverlap`, and whether the new
   groups have a header) rather than adding a second effect. Picking or leaving Shuffle needs nothing more: the order changes, so
   `ScrollToTopWhenChanged` already takes the list to the top (`SongGroups.orderKey`).
4. `screens/songs/CLAUDE.md`: in the Shuffle section, add that the bar fills and empties on its spring when the list
   loses or gains its headers (Shuffle picked or left, a selection starting or ending under it), as for a search;
   only the step from the loading placeholder to the first content is drawn in place. Reword the section's first bullet
   ("Before the first search interaction, arriving section headers are laid out expanded…") to name the loading step
   instead of the first search interaction.

## Tests

A UI test in `tools/screenshots` (read `tools/screenshots/CLAUDE.md` first: one app per test through `runAppTest`, the
composition on the test clock), `SongHeaderlessBarTest`, next to `SongFilterSearchTest`:

- With the demo library (alphabetical sort, search never opened), open the sort menu and pick Shuffle with
  `mainClock.autoAdvance = false`; step a frame at a time and read the top of the first song card (its bounds in the
  semantics tree). Assert that it reaches its new place over several frames and never moves more than half the distance
  in one frame; then pick Title again and assert the same on the way back.
- Under Shuffle, start selection mode from a song's menu and assert the card the menu was opened on keeps its top
  (within a pixel) on every frame while the bar empties.
- The guard the instant branch was written for (songs `CLAUDE.md`: "loading cannot keep a collapsed first row as a
  scroll offset"): after Shuffle → Title has settled, and after the first song is imported into an empty library
  (headerless placeholder, then a section, the latch already on), the list rests at the top with the first section
  header fully shown (`firstVisibleItemIndex == 0`, offset 0, the header its full height).
- Run the existing songs UI tests and `GoldenRendersTest` too: the launch frames must be unchanged (Fix 2).
