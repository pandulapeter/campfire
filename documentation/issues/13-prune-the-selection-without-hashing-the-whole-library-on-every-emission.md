# Prune the selection without hashing the whole library on every emission

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongSelection.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongSelectionTest.kt` (new)

## Problem

While the songs screen is in the selection mode, every `screenData` emission builds a hash set of every file name in the
library, on the view model's scope, which collects on the main thread (`SongSelection.kt` at ab97d3105):

```kotlin
fun startPruning(screenData: StateFlow<DataState<ScreenData>>) = scope.launch {
    screenData.collect { state ->
        val data = state.data
        if (state is DataState.Loading || data == null || !data.isWholeLibrary) return@collect
        val selected = selectedSongFileNames ?: return@collect
        val fileNames = data.unfilteredSongs.mapTo(hashSetOf()) { it.fileName }
        if (selected.any { it !in fileNames }) selectedSongFileNames = selected.filterTo(mutableSetOf()) { it in fileNames }
    }
}
```

`screenData` emits for far more than a change of the library: every filter chip and sort change, and every setlist write
(the selection's own Choose setlists sheet writes one per tap). For those, `unfilteredSongs` is the very same list
instance — `GetScreenDataUseCaseImpl` passes the song repository's list through as `unfilteredSongs = this` and builds
only the filtered half again — so the set is rebuilt from an unchanged library. For a library of several thousand
songs that is a few thousand allocations on the main thread per emission, in the same frames as the change the user
made.

The library-wide lookup `LibraryState.songLookup` was considered instead and is not a fit: it is built on
`Dispatchers.Default` after `screenData` and carries `isLibraryRead` (not loading), not `isWholeLibrary`, so a rescan's
partial batch could prune with it, which the documented rule forbids.

## Fix

Keep the set built for the last library and build it again only when `unfilteredSongs` is a different instance:

```kotlin
var prunedSongs: List<Song>? = null
var fileNames: Set<String> = emptySet()
screenData.collect { state ->
    ...
    val selected = selectedSongFileNames ?: return@collect
    if (data.unfilteredSongs !== prunedSongs) {
        prunedSongs = data.unfilteredSongs
        fileNames = data.unfilteredSongs.mapTo(hashSetOf()) { it.fileName }
    }
    if (selected.any { it !in fileNames }) selectedSongFileNames = selected.filterTo(mutableSetOf()) { it in fileNames }
}
```

The check against the selection still runs on every emission (it is linear in the selection, not the library), so a song
ticked between two emissions is still checked; the lists are immutable, so the same instance is the same library. The
cached set lives inside the coroutine and goes with it. Optionally drop the cached set when the mode ends (it is the size
of the library's names, held only while the view model lives); not needed for correctness.

Everything else stays: only a library read whole prunes, a rescan's batches do not, and Loading values are skipped.

## Tests

Add `SongSelectionTest` in `commonTest` (pure state over a fake flow, as `ListPlaceholdersTest` already builds
`ScreenData` values): with `runTest`, a `MutableStateFlow<DataState<ScreenData>>`, and `SongSelection(scope =
backgroundScope, backStack = listOf(CampfireDestination.Songs))`:

- a ticked song missing from a whole library is unticked;
- a second emission with the same `unfilteredSongs` instance and a different filter keeps the selection as it is;
- a song ticked after the first emission and missing from the library is unticked by the next emission, even when that
  emission carries the same `unfilteredSongs` instance — this pins that the cache never skips the check itself;
- an emission with `isWholeLibrary = false` prunes nothing.

## Manual check

On a phone with a large library, start the selection mode, tick a few songs, then change the sort and tap filter chips
several times: the list answers each tap as quickly as outside the mode, and the ticks stay. Then delete a ticked song
from another device and sync (or delete its file on the desktop and let the rescan finish): the count on the Done
button drops by one.
