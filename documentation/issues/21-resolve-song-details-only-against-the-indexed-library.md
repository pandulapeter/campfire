# Don't let a restored or deep-linked song details screen close itself, or open on the wrong page, before the library is indexed

**Challenged:** amended — named the test that constructs `IndexedSongs` (SongListStateTest) and how to fold the latch so it carries the state through.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android (restored process); web (a deep link to `song/…` or `setlist/…/…`) for scenario A
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/LibraryState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsSongs.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsSongsTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`

## Problem

`SongDetailsScreen` finds its songs in one flow and decides whether the library has been read from another. The two
flows don't arrive together. At b5c8ed3b5:

```kotlin
val songs = remember(destination, songsByFileName, songsBeingRenamed) {
    destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }.distinctBy { it.fileName }
}
val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
LaunchedEffect(songs.isEmpty(), isLoading) {
    if (songs.isEmpty() && !isLoading && viewModel.backStack.lastOrNull() == destination) onBack()
}
```

In `LibraryState.kt`, `isLoading` is a plain `runningFold` / `map` over `screenData`, collected on the main thread.
`songsByFileName`, on the other hand, comes from `indexedSongs`, which re-indexes the library first:

```kotlin
val indexedSongs = screenData.map { state -> IndexedSongInput(...) }.distinctUntilChanged().map { input ->
    IndexedSongs(input.sections, songSearchIndex.update(input.all, input.filtered), input.filterKey, input.sorted)
}.flowOn(Dispatchers.Default).asState(scope, IndexedSongs(emptyList(), SongSearchSnapshot.Empty, "", emptyList()))

val songsByFileName = indexedSongs.map { it.search.songsByFileName }.asState(scope, emptyMap())
```

So when the first read finishes (`DataState.Idle`), `isLoading` turns false at once. `songsByFileName` still holds the
previous value (empty, or the last partial batch) until `Dispatchers.Default` has folded the search keys of the whole
library and the result has hopped back. The KDoc of `indexedSongs` says this costs more than two frames for a whole
library.

The screen is composed before the read in two situations. The launch screen covers it, but it is still composed
underneath, since `CampfireContent` only waits for the preferences:
- **Android, restored process.** The back stack comes back from the `SavedStateHandle` with a `SongDetails` on top.
- **Web, deep link.** `FirstRunController.navigateOnLaunch` waits for `screenData` to leave `Loading` and then pushes
  the destination, while the index is still on its way.

**Scenario A: the screen closes itself.** In any frame between those two arrivals, `songs` is empty (the song is not
in the index yet) and `isLoading` is false. The effect calls `onBack()`, and the user ends up on the list instead of the
song they left. If the song was in an earlier partial batch it is already in the lookup, so this happens mostly with
small libraries, which reach `Idle` without any partial batch (`SongLocalSourceImpl` publishes only when
`songs.size >= publishedCount * 2`, and never the last batch), and with songs that come late in the scan.

**Scenario B: a setlist pager opens on the wrong song.** Partial batches *do* reach `songsByFileName`:
`SongRepositoryImpl.loadDataFromLocalSource` passes `onProgress = ::publishPartialData`, `BaseLocalDataRepository`
emits it as `DataState.Loading(data)`, and `indexedSongs` indexes `state.data` whatever the state. Take a restored
setlist screen over `[A, B, C, D]`, last on C (page 2). If the first partial batch holds A, C and D but not B:
- `songs` becomes `[A, C, D]`, and `SongDetailsPager` composes `SongPages`.
- `rememberPagerState` is saveable, so it restores page 2, which is now **D**.
- `FollowPager`'s `isInitialPageSettled` is a `rememberSaveable` and comes back `true`, so its one-time
  `scrollToPage(destination.initialIndex)` never runs.
- When B arrives the pager keeps its page by key (the page key is the file name), so it stays on D, now page 3.
- `onSongDetailsPageSettled` reports D, and that is where the user is from then on.

A fresh pager has the same problem: `isInitialPageSettled` false at a partial batch scrolls to `initialIndex` in the
partial list. That only happens on the web deep link path, and that path waits for a non-`Loading` state, so it does
not hit B.

## Fix

Publish "the library has been read" in the same value as the lookup. Then resolve a details screen only when either
every song it names is already there, or the library has been read. Until then the screen shows its loading
indicator and no pager.

**`LibraryState`**: carry the read state through the index, using the same latch that `isLoading` uses, so the two
can never disagree:

```kotlin
/** @param isLibraryRead False while the library is being read for the first time, as [isLoading] says. */
data class IndexedSongs(
    val sections: List<SongSection>,
    val search: SongSearchSnapshot,
    val filterKey: String,
    val sorted: List<Song>,
    val isLibraryRead: Boolean,
)
```

Fold `LoadingLatch` into `indexedSongs` with a `runningFold` before the `map`, as `isLoading` does. The fold has to carry the state with the latch (fold into a pair, or give the latch a `data` field) and drop its initial accumulator (`.drop(1)`, or `scan` started from the first value), or the first value indexed would be the fold's seed rather than `screenData`'s. Note that this chain collects `screenData` itself, upstream of `flowOn`, and the indexing map makes it a slow collector of a conflated `StateFlow`: it can miss the first `Idle` when a rescan's `Loading(previous)` follows at once (`BaseLocalDataRepository` emits one), and then reads "not read" until that rescan's `Idle`. That only delays the screen whose songs are not all there, which waits for the read anyway, so it is acceptable; do not try to share one fold with `isLoading` across dispatchers. Put `isLibraryRead`
into `IndexedSongInput` (so `distinctUntilChanged` lets the `Loading(full)` → `Idle(full)` step through) and copy it
into `IndexedSongs` in the second `map`. The index of an unchanged library is mostly reused by
`SongSearchIndex.update`, so the extra pass is cheap. Then:

```kotlin
/**
 * The whole library by file name, together with whether it has been read: one value, so that a screen resolving
 * songs from a destination never hears "read" before the songs it names are in the lookup (the index is built on
 * Dispatchers.Default and arrives after screenData, and with it isLoading).
 */
val songLookup = indexedSongs.map { SongLookup(it.search.songsByFileName, it.isLibraryRead) }.asState(scope, SongLookup.Empty)

val songsByFileName = songLookup.map { it.songsByFileName }.asState(scope, emptyMap())
```

`SongLookup` goes in the new `SongDetailsSongs.kt` (below), or in `state/` next to `LibraryState` if the executor
prefers. It is a small `data class SongLookup(val songsByFileName: Map<String, Song>, val isLibraryRead: Boolean)`
with `companion object { val Empty = SongLookup(emptyMap(), isLibraryRead = false) }`. Expose
`val songLookup: StateFlow<SongLookup>` on `CampfireViewModel` next to `songsByFileName`.

**`SongDetailsSongs.kt`** (new, pure, in `screens/songDetails/`):

```kotlin
/**
 * The songs a details screen pages through, or null while it cannot say yet: before the library has been read,
 * only a destination whose every song is already in the lookup is answered, since a partial batch holding some of a
 * setlist's songs would lay the pager out on the wrong page (a saved pager restores its page index, and keeps it by key
 * as the rest arrives), and an empty answer would close the screen.
 */
internal fun songDetailsSongsOf(songFileNames: List<String>, lookup: SongLookup, songsBeingRenamed: Map<String, Song>): List<Song>? {
    val songs = songFileNames.mapNotNull { lookup.songsByFileName[it] ?: songsBeingRenamed[it] }.distinctBy { it.fileName }
    return songs.takeIf { lookup.isLibraryRead || songs.size == songFileNames.distinct().size }
}
```

**`SongDetailsScreen`**: replace the `songs` / `isLoading` block with

```kotlin
val songLookup by viewModel.songLookup.collectAsStateWithLifecycle()
val resolvedSongs = remember(destination, songLookup, songsBeingRenamed) {
    // (keep the existing comment about renames and repeated keys)
    songDetailsSongsOf(destination.songFileNames, songLookup, songsBeingRenamed)
}
val songs = resolvedSongs.orEmpty()
LaunchedEffect(resolvedSongs?.isEmpty()) {
    // Every song this screen was opened on is gone from a library that has been read, and indexed: ...
    if (resolvedSongs?.isEmpty() == true && viewModel.backStack.lastOrNull() == destination) onBack()
}
```

Remove the `songsByFileName` and `isLoading` collections from this screen if nothing else in it reads them (check
before removing). `SongDetailsPager` already shows `DelayedLoadingIndicator` for an empty `songs`. `FollowPager` and
`rememberPagerState` need no change: a pager only composes over the complete list, so a restored page index means what
it meant before the process died, and a fresh pager's `scrollToPage(initialIndex)` lands in the list the index was
taken from.

The cost: a restored setlist screen with a song missing from the library (deleted by a sync run while the app was
dead) waits for the whole read instead of showing the partial list. The launch screen is up for part of that wait. A
single-song screen whose song is in an early batch still opens at once.

`DialogHost.startClosingWithSong` is not affected: it reads `allSongs`, which is mapped from `screenData` on the main
thread just like `isLoading`.

Docs:
- `screens/songDetails/CLAUDE.md`: where the screen closing itself when its songs are gone is described, add that this
  is judged against `songLookup` (the lookup and the read state from one value). Also add that before the read the
  screen only resolves a destination whose songs are all in the lookup, so a restored setlist pager never lays out
  over a partial batch.
- `ui/CLAUDE.md`, line 64's mention of `songsByFileName`: name `songLookup` as what the details screen reads.

## Tests

`SongDetailsSongsTest.kt` (commonTest, new, MPL header), over a few `Song` fixtures (reuse whatever builder
`SetlistSlotsTest` or `FakeSongFiles.kt` uses):
- Before the read, with `[a, b, c, d]` named and only `a, c, d` in the lookup: `null`.
- Before the read, with every name in the lookup: the songs, in destination order.
- Before the read, with a name in `songsBeingRenamed` instead: counted as resolved.
- After the read, with `a, c, d`: `[a, c, d]`, which is the setlist with a song gone, still shown.
- After the read, with none: an empty list, which closes the screen. Before the read, with none: `null`.
- A destination naming `a` twice resolves to one `a` and counts as complete.

The `LibraryState` change is wiring and is not unit-tested, like the rest of the class. `SongListStateTest.kt` (`commonTest/.../ui/state/`) constructs `LibraryState.IndexedSongs(...)` by name, so add `isLibraryRead = true` there (no default on the new field: every producer should say it), or `:presentation:desktopTest` stops compiling.

## Manual check

- **Android, scenario A.** With a small library (under 64 songs), open a song and press Home, then
  `adb shell am kill <application id>`, and reopen from Recents ten times. The song comes back every time and never
  falls back to the list.
- **Android, scenario B.** Use a library of a few hundred songs and a setlist whose songs are spread across the
  alphabet. Open the setlist and page to its third song, then Home, `am kill`, and reopen. The same third song is on
  screen and the pager bar says the same position.
- **Web.** Open `…/app/song/<a song>` and `…/app/setlist/<setlist>/<its third song>` in a fresh tab. Each opens on
  that song, and the second shows its correct position in the setlist.
