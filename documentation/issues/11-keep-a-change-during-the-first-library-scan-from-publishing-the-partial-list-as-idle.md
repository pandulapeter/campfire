# Keep a change made during the first library scan from publishing the partial list as finished

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (most visible where the first scan is slow: a large library, the web)
**Challenged:** amended — the partial publish's fold is made atomic (`_dataState.update`, reading the recorded changes inside the lambda) so an `updateData` racing a batch cannot be dropped; corrected the "change before the first read" note (today that `Idle` makes `loadDataIfNeeded` skip the read altogether, so the fix also cures that); consumer walk-through re-checked and recorded.
**Files:**
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepositoryTest.kt`
- `data/repository/implementation/CLAUDE.md` (if it describes the partial publish or `updateData`)

## Problem

The first read of the songs publishes its batches as `DataState.Loading(partial)` (`publishPartialData`, called by
`SongRepositoryImpl.loadDataFromLocalSource` through `songLocalSource.loadSongs(onProgress = ::publishPartialData)`),
and the partial list is on screen while the scan goes on (`CampfireViewModel.hasLibraryToShow` accepts a `Loading` with
songs in it). A change the user makes to one of those songs meanwhile — a tag from a song card's menu, a save, a new
song, a delete — goes through `updateData`, which turns whatever state it finds into `Idle`:

```kotlin
protected fun updateData(transform: (T?) -> T) {
    transformsDuringRead.update { it?.plus(transform) }
    _dataState.update { if (it is DataState.Failure) DataState.Failure(transform(it.data)) else DataState.Idle(transform(it.data)) }
}
```

So the half-read library plus the change is published as `Idle`, i.e. as the finished library. The existing test pins
exactly that (`BaseLocalDataRepositoryTest`, "a change that landed during a cancelled first read does not pass for the
library"):

```kotlin
repository.add("x")
assertEquals(DataState.Idle(listOf("a", "x")), repository.states.last())
```

Consumers that take `Idle` (or "not `Loading`") as "the library has been read":
- `CoverArtRepositoryImpl.init` collects `songRepository.songs.filterIsInstance<DataState.Idle<List<Song>>>()` and calls
  `coverArtLocalSource.keepOnlyCoverArt(keys)` — its own comment says it waits for Idle precisely so as not to prune
  "on the strength of half a library". With the flip it deletes the cached cover of every song not scanned yet; offline,
  those covers are gone until the device is online again.
- `CampfireViewModel.isLoading` latches `hasBeenRead` on the first `Idle`, ending the loading state early.
- `CampfireViewModel.navigateOnLaunch` waits for the first non-`Loading` screen data and resolves a launch address
  (the web's deep links) against it: a song not scanned yet is not found and the songs screen opens instead.
- `getScreenData(...).first { it !is DataState.Loading }` in the demo/Settings paths read a partial library as whole.

The flip also does not last: the scan's next `publishPartialData` puts `Loading(nextBatch)` back, and that batch comes
from the local source's own accumulator, without the change — so the changed song briefly reverts on screen until the
read ends and replays the change (`read()` re-reads once and folds `transformsDuringRead` onto the result, so the final
`Idle` is right).

## Fix

In `BaseLocalDataRepository`:

1. `updateData` keeps the kind of state it finds: `Failure` stays `Failure`, `Loading` stays `Loading` (with the
   transformed data), anything else becomes `Idle`:
   ```kotlin
   _dataState.update {
       when (it) {
           is DataState.Failure -> DataState.Failure(transform(it.data))
           is DataState.Loading -> DataState.Loading(transform(it.data))
           else -> DataState.Idle(transform(it.data))
       }
   }
   ```
   Check the cases this touches:
   - A re-read (`readOnce` with `previousData != null`) also sits in `Loading(previousData)`. A change during it now
     publishes `Loading(previous + change)` rather than `Idle`; the read ends in `Idle` anyway. `isLoading` is latched
     already, `hasLibraryToShow` is latched, and the CoverArt collector simply prunes once at the end — fine.
   - Before any read (`Loading(null)`), a change today produces `Idle(change alone)`; afterwards it is
     `Loading(change alone)`, which `loadDataIfNeeded` already re-reads (it re-reads any `Loading`). That is strictly
     better: a single new song is no longer passed off as the library. Check `SongRepositoryImpl.adoptImported` and
     `SetlistRepositoryImpl.adoptImported`, which test `first().data == null` and rescan — unchanged.
   - The cancellation branch of `readOnce` (`if (previousData == null) DataState.Loading(null) else DataState.Idle(...)`)
     is unchanged.
   - `transformAndWriteData` / `writeData` (preferences) are not touched: they publish `Idle` for whole-document
     writes, which only happen once there is data.
2. Make the partial publish carry the changes recorded so far, so the changed song does not revert under the user:
   ```kotlin
   protected fun publishPartialData(data: T) {
       if (!isPublishingPartialData) return
       _dataState.update { DataState.Loading(transformsDuringRead.value.orEmpty().fold(data) { result, transform -> transform(result) }) }
   }
   ```
   `update`, with the recorded changes read **inside** the lambda, not a plain `value =` assignment: `updateData` runs
   on any thread and records its transform before it updates the state, so a change that lands between this call
   reading the list and publishing would otherwise be overwritten by a batch without it (until the next batch or the
   end of the read). With `update`, that interleaving fails the compare-and-set and the retry reads the list that
   already holds the change.
   The class KDoc already requires every `updateData` transform to be safe to apply again ("each change replaces or
   drops the entries of the files it names with what is on disk now"), which is what makes this fold safe. Update the
   KDoc of `updateData` and `publishPartialData` to say what each now does.

Consumers re-checked against the new semantics (a `Loading` now lasts until the read ends, whatever changes land):
`CampfireViewModel.isLoading` and `hasLibraryToShow` treat a `Loading` with songs as a library to show, so the list
stays up with the change in it; `emptyPlaceholder` answers `LOADING` a little longer for an empty partial list, which is
right; `navigateOnLaunch` and the demo / Settings `first { it !is DataState.Loading }` wait for the whole library,
which is the point; `GetScreenDataUseCaseImpl`'s combination maps `Loading` through unchanged; the cover collector
prunes once, at the end. `UserPreferencesRepositoryImpl` never calls `updateData`. Note one remaining transient that
this plan leaves alone: when a change lands during a first read, `readOnce` still publishes `Idle(scan)` before the
one re-read `read()` makes, and that scan may lack the change; the cover collector can prune on it (only the cover of
the song just changed is at stake, and it is fetched again). Fixing that means not publishing `Idle` from `readOnce`
while a re-read is due — a separate change.

Do not change `SyncRepositoryImpl` (`if (songRepository.songs.first() is DataState.Loading) songRepository.loadSongsIfNeeded()`):
it still loads when it finds `Loading`, which `loadDataIfNeeded` serialises behind the running read.

## Tests

In `BaseLocalDataRepositoryTest` (`:data:repository:implementation:desktopTest`):
- Change the existing "a change that landed during a cancelled first read does not pass for the library" assertion to
  `DataState.Loading(listOf("a", "x"))`.
- New: "a change during a first read is not published as the library" — batches `[["a"], ["a","b"]]`, gate the load,
  `add("x")`, assert no state in `states` is an `Idle` until the gate opens, then the last state is
  `Idle(listOf("a", "b", "x"))`.
- New: "a partial publish after a change keeps the change" — batches `[["a"], ["a","b"], ["a","b","c"]]`, with `onRead`
  or a gate between partial batches adding `"x"` after the first one; assert every `Loading` published after the add
  contains `"x"`. (`TestRepository.loadDataFromLocalSource` publishes all partial batches before awaiting the gate;
  move the `gate?.await()` between the first and the remaining partial publishes, or add a second gate, to place the
  change between two batches.) Note `add` appends, so a fold that applies it twice would show `"x"` twice: make the
  test's change idempotent (`it.orEmpty().filterNot { v -> v == item } + item`) to match the contract of real callers.
- New: "a change before the first read is not passed off as the library" — no load, `add("x")`, assert the last state
  is `Loading(listOf("x"))`, then `load()` reads the source and returns what it scanned (a real change is on disk, so
  the scan finds it; the test source does not hold `"x"`, so expect the batches' last list). Note that this also fixes a
  second bug: today the change publishes `Idle(["x"])`, and `loadDataIfNeeded` (`takeUnless { it is Loading ||
  hasReadFailed }`) then returns `["x"]` **without reading the library at all**. After the fix the read happens; it
  starts with data on screen (`previousData = ["x"]`) and so publishes no partial batches, which is acceptable for a
  case no launch path reaches today (the screens ask for the library before anything can change it, and
  `adoptImported` rescans when it finds no data).

## Manual check

On a phone or the web with a library large enough that the first scan visibly fills the list (a few thousand songs,
or a throttled web build), with cover art cached for songs near the end of the list: launch, and while the list is
still filling, add a tag to one of the first songs from its card menu. The tag stays on the card while the scan goes
on, the loading indicator stays until the scan ends, and once offline afterwards the covers of the songs at the end of
the list still show.
