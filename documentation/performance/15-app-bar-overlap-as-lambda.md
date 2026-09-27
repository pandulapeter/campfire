<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 15 — Read the app bar overlap in layout, not in composition

| | |
|---|---|
| Lane | C |
| Impact | medium–high |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Search.kt |
| Depends on / conflicts with | Conflicts with 19 (both change the `SectionHeader` signature and the pushed-header block), 20 and 22 (same files). Land 15 first. |
| Commit message | `Keep the search's app bar animation from recomposing the song and setlist lists on every frame.` |

## Problem
`SongsScreen.kt:171` and `SetlistsScreen.kt:144` build the overlap during composition:

```kotlin
appBarOverlap = AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value),
```

`appBarReveal` is the `animateFloatAsState` spring from `animateAppBarReveal` (`Search.kt:380-389`, `searchTravelSpec()`, about 400 ms). It runs every time the search opens or closes, and whenever a placeholder appears or goes away (`isShownWithoutSearch`). Reading `.value` in composition has these effects:

- The screen recomposes on every frame of the spring.
- `AppBarOverlap` is a data class whose `coverage` and `height` change on every frame, so `SongList` and `SetlistList` never skip.
- The `LazyVerticalGrid` content lambda captures `appBarOverlap`, because the sticky headers pass it to `SectionHeader` (`SongsScreen.kt:376`, `SetlistsScreen.kt:325`). A new content lambda therefore arrives on every frame. With it, the grid:
  - rebuilds its whole interval list: one `stickyHeader` and one `itemsIndexed` per group, which is about 1,000+ intervals for a 2,000-song library sorted by artist;
  - rebuilds the span-layout provider, which walks lines linearly up to the first visible item, and the key-index map, which allocates `"song_${fileName}"` strings;
  - reruns the body of every visible item, including the uncached `viewModel.renderKey(...)` transposition (`SongsScreen.kt:392`, `SetlistsScreen.kt:426`).
- This happens on the same frames as the keyboard animation and the field taking the focus.
- It also contradicts the intent stated on `Modifier.underAppBar` (`Search.kt:421-429`), which says the bar filling in "moves the list without recomposing it".

The overlap's values are only needed in three places:

- in `SectionHeader`'s `.layout {}` block, which reads `reach` and `coverage` (`ListItems.kt:565-580`);
- in the fast scroller's top padding, `.padding(top = appBarOverlap.height)` (`SongsScreen.kt:469`, `SetlistsScreen.kt:508`);
- in the pushed header copy, which passes it on to `SectionHeader`.

## Fix
1. `SectionHeader` (`ListItems.kt:537-551`): change the parameter to `appBarOverlap: () -> AppBarOverlap`. Inside the existing `.layout {}` block, call `val overlap = appBarOverlap()` once, then use `overlap.reach` and `overlap.coverage`. Update the KDoc `@param` to say it is read while laying out.
2. In both screens, keep the reveal state out of composition:
   ```kotlin
   var appBarReach by remember { mutableStateOf(0.dp) }
   val appBarReveal = animateAppBarReveal(...)
   val appBarOverlap: () -> AppBarOverlap = remember(appBarReveal) {
       { AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value) }
   }
   ```
   The lambda captures the delegated `MutableState`, so `appBarReach` is read lazily. Pass it down to `SongList`/`SetlistList` with the type `() -> AppBarOverlap`, and from there to every `SectionHeader`, the pushed copy included.
3. Replace `.padding(top = appBarOverlap.height)` on the `FastScroller` with a layout modifier. Put it in `Search.kt` next to `underAppBar`:
   ```kotlin
   /** Lays a list's overlay out below the part of the app bar the list is still under, read while laying out. */
   internal fun Modifier.belowAppBarOverlap(appBarOverlap: () -> AppBarOverlap) = layout { measurable, constraints ->
       val top = appBarOverlap().height.roundToPx()
       val placeable = measurable.measure(constraints.offset(vertical = -top))
       layout(placeable.width, placeable.height + top) { placeable.placeRelative(0, top) }
   }
   ```
   Chain it where the padding was: `.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap).padding(contentPadding.only(...))`.

What must not change: headers must narrow and widen along exactly the same eased curve, and the scroller must start below the buttons exactly as it does now. The values are the same; only where they are read moves. `AppBarOverlap` itself stays as it is.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run`, and compile Android with `./gradlew :app:android:assembleDebug`.
- Manual check with a large library sorted by artist, scrolled so that a header is pinned:
  1. Open and close the search.
  2. The pinned header should widen and narrow as before, and the fast scroller's top should follow the bar.
- Measurement: in Android Studio's Layout Inspector, open the search with recomposition counts on. Visible song rows should gain no recompositions from the animation, where before they gained one per frame. Alternatively, a system trace should show no `LazyVerticalGrid` content rebuild during the spring.
- Update the `SectionHeader` KDoc `@param appBarOverlap`. `presentation/CLAUDE.md` names `AppBarOverlap.coverage` behaviorally only, so it needs no change.
