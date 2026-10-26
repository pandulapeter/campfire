<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Tell the list placeholders apart from the lists alone, so that new library data never pairs with a list not yet rebuilt

**Challenged:** amended — counts carried in the list alone still flash when an import ends: `isImporting` is a live
Main-thread flag (`ImportController.applyImportPlan`'s `finally`), so an import's first songs or setlists in an empty
library go LOADING → (import over, list not rebuilt, `libraryCount == 0`) **NO_SONGS / NO_SETLISTS** → the list: "Your
library is empty" over the demo library on a first run, exactly what `emptyPlaceholder`'s `isImporting` KDoc says it
prevents, where the old code flashed "no songs match" instead. Added step 3a: while the list is behind the library
(`screenData` holds data whose count differs from the list's `libraryCount` / `librarySongCount`), the placeholder
keeps the value it had, so it never answers for a list that is not the library's yet. A test of that helper added. The
rest checked: `isLibraryRead` (the `LoadingLatch`, true on Failure and Idle, never false again), the Setlists bar key
of 188635afd, search (`copy` keeps the counts), archive, Songs switched off, `LaunchState`; no strings change.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (longest on low-end Android and the web)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ListPlaceholders.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/ListedSetlists.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongGroups.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/setlists/SetlistsController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/SongListState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchState.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ListPlaceholdersTest.kt`

## Problem

Each placeholder combines `screenData` (on Main) with lists built from it `flowOn(Dispatchers.Default)`, which arrive a
few frames later. `SetlistsController.setlistsPlaceholder`:

```kotlin
val setlistsPlaceholder = combine(screenData, setlistsWithSongs, visibleSetlists, isImporting) { screenData, setlistsWithSongs, visibleSetlists, isImporting ->
    setlistListPlaceholder(screenData = screenData, setlistsWithSongs = setlistsWithSongs.setlists, visibleSetlists = visibleSetlists.setlists, isImporting = isImporting)
}.asState(scope, Placeholder.LOADING)
```

and `setlistListPlaceholder` (`state/ListPlaceholders.kt:44-49` at 54829e6fd):

```kotlin
setlistsWithSongs.isNotEmpty() -> null
screenData.data?.setlists.isNullOrEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SETLISTS, isImporting)
visibleSetlists.isEmpty() -> Placeholder.ALL_SETLISTS_HIDDEN
```

When the first setlist lands (made from the placeholder in a library with no songs, so no picker follows; or brought in
by sync or an import), `screenData` has it while `visibleSetlists` is still the old empty list: the screen crossfades
NO_SETLISTS → "every setlist is archived" → the list. 5c1cc3e64 only hid this at launch (`LaunchState.hasLibraryToShow`).

The songs side has the same pairing, confirmed by reading: `songGroups` is built `flowOn(Dispatchers.Default)` in
`SongListState`, and `songListPlaceholder` reads

```kotlin
data == null || data.unfilteredSongs.isEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SONGS, isImporting)
data.songs.isEmpty() -> Placeholder.ALL_SONGS_HIDDEN
else -> Placeholder.NO_MATCHING_SONGS
```

so the first songs arriving in an empty library (an import's first batch, a sync, the demo library) show "no songs
match" for the frames until `songGroups` catches up; `isImporting` does not help, since it is read only in the empty
branch. (Not watched in a running app; the review was lean. The flows say it.)

## Fix

Carry the counts each placeholder needs inside the list value, built in the same pass, and read `screenData` only for
Loading / Failure (through `emptyPlaceholder`):

1. `ListedSetlists` gains `libraryCount: Int` (setlists in the library) and `unsearchedCount: Int` (left after the
   archive filter, before the search), KDoc'd; `Empty` has 0 for both. `visibleSetlists` sets them
   (`setlists.size`, `visible.size`); `setlistsWithSongs` keeps them through its `copy`.
2. `SongGroups` gains `librarySongCount: Int` and `filteredSongCount: Int` (before the search), from
   `indexed.search.byFileName.size` and `indexed.search.filtered.size` in `SongListState.songGroups`; the initial value
   has 0 for both.
3. `setlistListPlaceholder(screenData, listed: ListedSetlists, isImporting)`:

   ```kotlin
   listed.setlists.isNotEmpty() -> null
   !listed.isLibraryRead -> Placeholder.LOADING
   listed.libraryCount == 0 -> screenData.emptyPlaceholder(Placeholder.NO_SETLISTS, isImporting)
   listed.unsearchedCount == 0 -> Placeholder.ALL_SETLISTS_HIDDEN
   else -> Placeholder.NO_MATCHING_SETLISTS
   ```

   and `songListPlaceholder(screenData, songGroups, isImporting)` the same shape with `librarySongCount` /
   `filteredSongCount`, `ALL_SONGS_HIDDEN`, and `NO_MATCHING_SONGS`. (`isLibraryRead` is true on Idle and Failure,
   so an unreadable library still reaches `ERROR` through `emptyPlaceholder`; while the list is the initial `Empty`
   the screen shows the loading placeholder, as before.) `setlistsPlaceholder` combines `screenData`,
   `setlistsWithSongs`, and `isImporting` only; `visibleSetlists` stays private.
3a. **Hold while the list is behind.** Add `internal fun isListBehind(screenData: DataState<ScreenData>, listedCount: Int,
   libraryCount: (ScreenData) -> Int): Boolean` (or one per list) in `ListPlaceholders.kt`: true where `screenData.data`
   is not null and `libraryCount(data) != listedCount` (`data.setlists.size` against `ListedSetlists.libraryCount`,
   `data.unfilteredSongs.size` against `SongGroups.librarySongCount`). `setlistsPlaceholder` and `songsPlaceholder`
   drop the combinations where it is true (`.filter { … }` before `.map`/`asState`, or a `combine` that computes the pair
   and a `filterNot`), so the state keeps its last value until the list built from this library arrives: LOADING through
   an import into an empty library (the import's end no longer uncovers a stale count of zero), NO_SETLISTS while the
   first setlist made from the placeholder is listed, the list itself while the last setlist is deleted. Both counts come
   from the same `screenData` (`LibraryState.setlists` / `indexedSongs` read it), so they always meet again; data that is
   null (a first read, a Failure with nothing read) is never "behind", and falls through to `emptyPlaceholder` as before.
   KDoc it: a placeholder only answers for a list built from the library it is shown with.
4. Keep `LaunchState.hasLibraryToShow`'s condition as it is (it still keeps the launch screen up over the lists'
   loading placeholders), but correct its KDoc, which gives "the setlists screen would say for those frames that every
   setlist the read found is archived" as the reason: it would now show its loading placeholder. Same correction in
   `ui/launch/CLAUDE.md` ("… or the songs bar's New button lands late and setlists flash as archived" → "… and the
   setlists screen shows its loading placeholder").
5. Update the KDoc of both placeholder functions and of `setlistsPlaceholder` / `songsPlaceholder` to say the list value
   carries the counts it was built from, so that the placeholder never pairs a list with a newer library.

## Tests

`ListPlaceholdersTest` (`./gradlew :presentation:desktopTest`): adapt the existing cases to the new signatures, and add
"a list not yet rebuilt for new library data keeps the placeholder it had": `setlistListPlaceholder` with `screenData`
holding one setlist and a `ListedSetlists` with `libraryCount = 0, isLibraryRead = true` returns `NO_SETLISTS` (not
`ALL_SETLISTS_HIDDEN`); `songListPlaceholder` with `screenData` holding a song and `SongGroups` with
`librarySongCount = 0, isLibraryRead = true` returns `NO_SONGS` (not `NO_MATCHING_SONGS`); and both return `LOADING`
for a list with `isLibraryRead = false`. And "a list behind the library holds the placeholder": `isListBehind` is true for `screenData` holding
one setlist and a list counting none (and for songs likewise), false for matching counts and for `DataState.Loading(null)`
/ `DataState.Failure` with no data. Check `tools/screenshots` still compiles (`Shot.kt` only reads `SongGroups`).
