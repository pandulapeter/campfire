# Let a tag filter that changes nothing in the list consume its anchor, instead of leaving it to jump the list later

**Challenged:** amended — the Problem holds (a tag filter over a list of songs that all carry it emits no new `ScreenData`, so the anchor stays pending), but the Fix's step 1 was not executable as written: `SongListPreferences` is a `private` class inside `GetScreenDataUseCaseImpl` (not visible from `:domain:api`), the filter and the preferences are in the transform of the *song-part* `combine`, not of the final one that builds `ScreenData`, and `GetScreenDataUseCaseImplTest` has no `ScreenData(` constructions to update. Step 1 rewritten; the rest kept.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (`indexedSongs`, `songGroups`, `songsPlaceholder`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItemAnimation.kt` (KDoc only),
`presentation/CLAUDE.md` and `domain/implementation/CLAUDE.md` where they describe the song list and the tapped tag keeping the row in place

## Problem

Tapping a tag on a song row filters the list and keeps that row where it was: `SongsScreen` (at 9ab7ca54e) stores an
anchor and toggles the filter,

```kotlin
val onTagClicked: (Song, String) -> Unit = remember(viewModel, listState) {
    { song, tag ->
        filterAnchor.set(listState, songItemKey(song))
        viewModel.toggleTagFilter(tag)
    }
}
```

and `ScrollToTopWhenChanged` takes the anchor when the filter key changes and consumes it when the *contents* change,
compared by identity:

```kotlin
val hasKeyChanged = key != lastScrollToTopKey
if (hasKeyChanged) {
    lastScrollToTopKey = key
    heldTop.isHolding = true
    heldTop.anchoredItem = anchor?.take()
}
val anchoredItem = heldTop.anchoredItem
if (anchoredItem != null) {
    if (contents !== heldTop.contents) {
        heldTop.anchoredItem = null
        val index = itemIndex(anchoredItem.key)
        if (index == null) {
            listState.requestScrollToItem(0)
        } else {
            listState.requestScrollToItem(index = index, scrollOffset = -anchoredItem.offset)
            anchor?.animateFrom(anchoredItem.visibleOffsets, coroutineScope)
        }
    }
}
```

The key is built by the screen from the preferences and the filter state,

```kotlin
key = "$searchedQuery|${userPreferences?.sortingMode?.name}|${songFilter.selectedTags.sorted()}|${userPreferences?.tagMatchMode?.name}|${songFilter.selectedLanguages.sorted()}|${userPreferences?.languageMatchMode?.name}",
contents = songGroups,
```

while the contents are `viewModel.songGroups`: `screenData` (the filter is applied in `GetScreenDataUseCaseImpl`)
mapped to `IndexedSongInput` behind a `distinctUntilChanged()`, combined with the search query and exposed through
`asState`, which is `distinctUntilChanged().stateIn(...)`; `SongGroup` and `Song` are data classes. When the filter
changes but the filtered list is equal to the one before it — every visible song carries the tapped tag, which is what
a fresh installation's two `Demo` songs look like, or any library where one tag is on everything — no new value is
emitted, the contents keep their identity, and the anchor stays pending. It is dropped by the next scroll, but on a
list too short to scroll, or one nobody touches, it lives on until the next real change of the list — a sync run, an
edit, a deletion minutes later — and then fires: the list jumps to the anchored row's stale offset, or to the top when
that song has since been deleted, and the anchor transition animates from offsets recorded long ago.

The key and the contents also arrive in different compositions today (the preference is written, then the use case
recomputes), which the anchor tolerates only because the contents always change in the common case.

## Fix

Let the list say what it was built for, so that the key and the contents are one value and arrive together, and two
equal lists for two filters are two values.

1. `ScreenData` (`domain/api/.../models/ScreenData.kt`) gains the inputs its song list was narrowed and sorted by, as
   plain values the api module can see: `val songFilter: SongFilter = SongFilter()`,
   `val sortingMode: UserPreferences.SortingMode`, `val tagMatchMode: UserPreferences.MatchMode` and
   `val languageMatchMode: UserPreferences.MatchMode` (defaults so that `partialScreenData` and any other constructor
   call need not name them; check that `UserPreferences` is reachable from `:domain:api`, it is used by
   `SongListPreferences` in the impl, so it lives in `:data:model`). In `GetScreenDataUseCaseImpl`, the private
   `SongPart` (and `EMPTY_SONG_PART` in the companion) gains the same four fields, `toSongPart(songListPreferences, filter)`
   fills them from its two parameters, and both `ScreenData(...)` calls (in the final `combine` and in
   `partialScreenData`) pass them on from `songPart`. The only two constructions of `ScreenData` in the repository are
   those two; the test builds it through `collectScreenData()`.
2. In `CampfireViewModel`, `IndexedSongInput` carries them too, so that its `distinctUntilChanged()` lets an equal
   list under a new filter through; `IndexedSongs` carries a `filterKey: String` built from them (the same six-part
   string the screen builds today, minus the query); and `songGroups` emits

   ```kotlin
   /** The song list with the filter, the sort and the query it was built for: two equal lists for two filters are two values. */
   data class SongGroups(val filterKey: String, val groups: List<SongGroup>)
   ```

   with `filterKey = "${normalizedQuery}|${indexed.filterKey}"`. `songsPlaceholder` and every other reader take `.groups`.
3. `SongsScreen` reads `key = songGroups.filterKey` and `contents = songGroups` (the wrapper), and drops its own key
   expression; `sectionIndex` and `itemIndexOf` use `songGroups.groups`. `hasKeyChanged` and `contents !== heldTop.contents`
   are now true in the same `SideEffect`, so the anchor is taken and consumed at once, and there is nothing left to go
   stale. `SetlistsScreen` keeps its own key (it passes no anchor).

Do not fix it inside `ScrollToTopWhenChanged` with a frame count or a delay: the gap between the tap and the emission
is a dispatch, not a duration, and the project fixes ordering with state, never with time. Say in the KDoc of
`ScrollToTopWhenChanged`'s `contents` parameter that the caller hands it a value that changes with the key, and
mention in `presentation/CLAUDE.md`'s song list paragraph that the list carries its filter key.

## Tests

`GetScreenDataUseCaseImplTest`: the emitted `ScreenData` carries the filter and the three preferences it was built with
(one assertion in an existing test), and a filter that changes to one that leaves the same songs still emits a new
value (this one fails with the bug in place: `ScreenData` is equal, the flow conflates it). The anchor itself has no pure test; the `presentation` tests do not build the
view model.

## Manual check

Fresh installation on the desktop build (two demo songs, both tagged `Demo`): tap the `Demo` pill on the second row,
then edit one of the songs' titles in the editor and save. The list does not jump when the edit lands. Then, with a
library of a few hundred songs, tap a tag on a row far down the list: the row stays where it was, as before.
