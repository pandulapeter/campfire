# Rebuild the song picker's songs only when the library or its order changes

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongPickerState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/search/SongPickerIndex.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/search/` (a new or existing picker index test)

## Problem

`SongPickerState.pickerSongs` rebuilds a `PickableSong` for every song of the library on every `indexedSongs` emission
(`SongPickerState.kt` at ab97d3105):

```kotlin
val pickerSongs = indexedSongs.map { indexed ->
    val list = indexed.sorted.mapNotNull { indexed.search.byFileName[it.fileName] }.map { it.toPickableSong() }
    PickerSongs(list = list, byFileName = list.associateBy { it.song.fileName })
}
    .flowOn(Dispatchers.Default)
    .asState(scope, PickerSongs.Empty)
```

and `toPickableSong()` (`SongPickerIndex.kt`) allocates two new sets per song:

```kotlin
tags = song.tags.mapTo(mutableSetOf()) { it.lowercase() },
...
languages = song.languages.ifEmpty { listOf(SongLanguage.UNKNOWN) }.toSet(),
```

`indexedSongs` (`LibraryState`) emits whenever its input changes, which includes the songs screen's filter: a tag or
language chip changes `filtered`, `sections`, and `filterKey` while the library and its order stay the same. The order is
memoized by the domain layer (`sortMemo` keeps `sortedSongs` the same instance while the library and the sorting mode
are), and `SongSearchIndex.update` hands back the same `SearchableSong` instances for unchanged songs, though always in
a new map. So every filter tap on the songs screen has a whole library's `PickableSong`s and their sets built again on
`Dispatchers.Default` — competing with the main thread for a low-end phone's few cores in the very frames the filtered
list is being laid out — and, since `PickerSongs` is a plain class, the state flow takes the equal result as a new value
and re-emits it, so an open picker would also recompose for nothing.

## Fix

Return the previous `PickerSongs` when nothing it is made of changed. `sortedSongs` is the domain layer's
`sortMemo` output, which is the same instance exactly while the library list and the sorting mode are the same, and the
same library list means the search index hands back the same `SearchableSong` instances. So the order's identity is the
whole test:

1. **Recommended:** extract the mapping into a pure function next to `PickerSongs` (in `SongPickerIndex.kt`) that takes
   the previous result and the list it was built from, and returns the previous result when `sorted` is the same
   instance:

   ```kotlin
   internal fun pickerSongsOf(sorted: List<Song>, byFileName: Map<String, SearchableSong>, previous: PickerSongs?): PickerSongs
   ```

   keeping the source `sorted` on `PickerSongs` (an `internal` field, or a small holder the state keeps beside it) to
   compare against. `SongPickerState` keeps the last result in the flow (`runningFold`, or a local captured by `map`) and
   passes it in. The same instance then reaches `asState`, and the state flow emits nothing.
2. In addition, optionally: when `sorted` did change (a library write, a new sort), reuse the previous `PickableSong` of
   every song whose `SearchableSong` is the same instance, so one song saved rebuilds one `PickableSong` rather than all
   of them. This needs the source `SearchableSong` kept per entry; worth it only if a library write measures slow.

Comparing `indexed.search.byFileName` by instance, as first proposed, would never hit: `SongSearchIndex.update` builds a
new map on every update. Drop this plan if `sortedSongs` turns out not to be the same instance across a filter change on
the current code (check `GetScreenDataUseCaseImpl.sorted`'s `sortMemo`).

Everything the picker shows stays the same: the same songs, in the songs screen's order, with the same search and
filter keys.

## Tests

Pure logic, in `commonTest` beside the picker index's existing tests (or a new `PickerSongsTest`):

- the same `sorted` instance with a new map holding the same `SearchableSong` instances returns the previous instance;
- a new `sorted` list (another order) returns a new instance with the songs in the new order;
- a new `sorted` list in which one song was replaced returns a new instance carrying the new song (and, with option 2,
  every other `PickableSong` the previous instance);
- a song removed from the library is gone from `list` and `byFileName`.

## Manual check

On a low-end phone with a large library, on the Songs screen tap tag and language chips quickly: the list answers each
tap promptly. Then open a setlist's Choose songs sheet: it lists every song in the songs screen's order, its search and
its tag and language chips work, and a song edited meanwhile (a new tag added from its details screen) shows and filters
by its new tag.
