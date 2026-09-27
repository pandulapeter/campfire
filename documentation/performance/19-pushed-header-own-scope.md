<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 19 — Keep the pushed section header and the header fractions out of the list's composition

| | |
|---|---|
| Lane | C |
| Impact | medium–low (per frame while a header is being pushed, which with the songs sorted by artist is a large share of any scroll) |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt |
| Depends on / conflicts with | After 15, which changes `SectionHeader`'s `appBarOverlap` to a lambda. Conflicts with 20 and 22 (same screen files). |
| Commit message | `Move the pushed section header and the header scroll fractions out of the list's recompositions.` |

## Problem
1. `SongsScreen.kt:304` reads `val pushedHeader = pushedSectionHeader(listState, contentType = "header")`, and `SetlistsScreen.kt:196` does the same.
   - `pushedSectionHeader` (`ListItems.kt:466-487`) is a value-returning composable that reads a `derivedStateOf`. A value-returning composable has no restart scope of its own, so the read invalidates the caller, `SongList`/`SetlistList`.
   - While a header is being pushed off the top, the derived `PushedSectionHeader` changes every frame (its `offset`, `visibleFraction` and `pushedDistance`). So the whole list body recomposes every frame.
   - On the songs screen, that body collects about 10 states again and rebuilds the `ScrollToTopWhenChanged` key (`SongsScreen.kt:309`, including `selectedTags.sorted()` and `selectedLanguages.sorted()`).
   - It also recomposes `LazyVerticalGrid`, because `Modifier.listTopFadeViewport(topFade)` (`EdgeFade.kt:89`) is a non-composable factory whose `onPlaced` lambda is new on every call, so the grid's modifier is never equal.
   - `SetlistList` additionally runs `rowsCache.retainOnly(...)` (a set allocation) and `rememberReorderableLazyGridState`. At `SetlistsScreen.kt:488` it runs `setlistsWithSongs.firstOrNull { "setlist_${it.setlist.fileName}" == pushed.key }`, which is one string per setlist per frame.
   - With the songs sorted by artist, every section has a push phase: 56dp of scroll out of every section's height.
2. `sectionHeaderState(listState, headerIndex)` (`ListItems.kt:490-508`) also returns a value read in the sticky header item's scope (`SongsScreen.kt:368-375`, `SetlistsScreen.kt:304-325`).
   - The arriving and outgoing headers get a new `SectionHeaderState` on every frame of the push, so both header items recompose every frame (Surface, Row, `AnimatedVisibility`, Text).
   - Yet `pinnedFraction` is only used inside `SectionHeader`'s `.layout {}` (`ListItems.kt:574`).
   - `visibleFraction` is only turned into `opacity`, which is used in a `graphicsLayer {}` (`:554-557`).
   - The pushed copy's `contentOpacity` and `pushedDistancePx` are also only used in a `graphicsLayer {}` (`:581-585`).

## Fix
1. `ListItems.kt`:
   - Make `pushedSectionHeader` return `State<PushedSectionHeader?>`: return the `remember { derivedStateOf { ... } }` itself rather than delegating to it.
   - Make `sectionHeaderState` return `State<SectionHeaderState>` the same way. A rename to `rememberSectionHeaderState` fits.
   - Change `SectionHeader`'s `state: SectionHeaderState` to `state: () -> SectionHeaderState`, read only inside the `.layout {}`.
   - Change `opacity: Float` to `opacity: () -> Float`, and `contentOpacity`/`pushedDistancePx` to `() -> Float` / `() -> Int`, all read only inside their `graphicsLayer {}` blocks. Update the KDoc.
2. Each screen gets a private overlay composable, so that only it restarts:
   ```kotlin
   @Composable
   private fun PushedSongSectionHeader(listState: LazyGridState, sectionIndex: SongSectionIndex, endPadding: Dp, appBarOverlap: () -> AppBarOverlap) {
       val pushed = pushedSectionHeader(listState, contentType = "header")
       val key by remember(pushed) { derivedStateOf { pushed.value?.key } }  // changes once per section, not per frame
       val header = key?.let(sectionIndex::headerForKey) ?: return
       SectionHeader(
           modifier = Modifier
               .layout { m, c ->                     // offset and width, read while laying out
                   val p = pushed.value ?: return@layout layout(0, 0) {}
                   val placeable = m.measure(c.copy(minWidth = p.width, maxWidth = p.width))
                   layout(placeable.width, placeable.height) { placeable.place(p.offset) }
               }
               .clearAndSetSemantics {},
           text = header.displayText(),
           state = { SectionHeaderState(visibleFraction = pushed.value?.visibleFraction ?: 0f, pinnedFraction = 1f) },
           contentOpacity = { pushed.value?.visibleFraction ?: 0f },
           pushedDistancePx = { pushed.value?.pushedDistance ?: 0 },
           ...
       )
   }
   ```
   Call it from `SongList` in place of the `pushedHeader?.let { ... }` block.
   - The setlists screen gets the same overlay, with the setlist found by key through `remember(setlistsWithSongs) { setlistsWithSongs.associateBy { "setlist_${it.setlist.fileName}" } }`.
   - Keep the placement exactly as the old `offset { pushed.offset }` combined with `width(...)`. The overlay is a child of the list's `Box`, so the old `IntOffset` still applies.
3. In the sticky headers:
   ```kotlin
   val headerState = sectionHeaderState(listState, headerIndex)
   SectionHeader(state = { headerState.value }, opacity = { if (headerState.value.visibleFraction < 1f) 0f else 1f }, ...)
   ```
4. In both lists, remember the grid's viewport modifier: `val gridModifier = remember(topFade) { Modifier.fillMaxSize().listTopFadeViewport(topFade) }`.

What must not change:

- The pushed header's position, its fade and its parallax, frame for frame.
- The pinned header's narrowing curve.
- The fact that the pinned header is hidden (opacity 0) while it is being pushed.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run`, and the Android debug build.
- Manual check, with the songs sorted by artist and on the setlists screen:
  - Scroll slowly through several section boundaries. The outgoing header must fade, lag and be clipped exactly as before, and the incoming one must narrow as it reaches the bar's buttons.
  - Also check this with the search open.
- Measurement: in Layout Inspector's recomposition counts during a slow scroll across a boundary, `SongList`/`SetlistList` and the header items should stay flat, and only `PushedSongSectionHeader` should count, about once per section.
