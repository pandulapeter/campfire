# Stop a library rescan from re-reading itself for as long as a sync run keeps refreshing files

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (in practice ios, desktop)
**Files:** data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt, data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepositoryTest.kt, data/repository/implementation/CLAUDE.md

## Problem
`BaseLocalDataRepository.read()` re-reads the whole local source for as long as any `updateData` lands while a read
is running, with no bound:

```kotlin
// BaseLocalDataRepository.kt:149-153
private suspend fun read(): T? {
    val updateCountAtStart = updateCount
    val data = readOnce() ?: return null
    return if (updateCount != updateCountAtStart) read() else data
}
```

During a sync run, `SyncRepositoryImpl.scheduleLiveRefresh` (SyncRepositoryImpl.kt:588-595) calls
`SongRepository.refresh(fileNames)`/`SetlistRepository.refresh`, i.e. `updateData`, every
`liveRescanPauseAfter(duration)` = `max(1 s, 5 × refresh duration)` (SyncRepositoryImpl.kt:892-898), so about once a
second. A first sync of a large library runs for minutes (Dropbox rate limiting, see root CLAUDE.md).

Whenever a full song scan takes longer than that interval (a library of a couple of thousand songs on a phone, or any
large one on the web), a rescan started during the run never finishes until the run does. Triggers that need no
unusual action:
- iOS and desktop: `CampfireApp.kt:214-217` calls `viewModel.refresh()` on every `ON_START` (iOS returning to the
  foreground, desktop window restored) and `refreshIfStale()` on desktop focus — so switching away from the app
  during a first sync and back starts such a rescan.
- The "Retry" of a failed read (`SongsScreen.kt:351`, `SetlistsScreen.kt:292`).

Cost while it loops: every song file read and parsed again every ~1–3 s for the rest of the run (CPU, battery, I/O
competing with the run's own reads); the state stays `DataState.Loading` the whole time (`readOnce` sets it at
line 159), so the loading indicator stays up; and the read holds `mutex`, so every `loadDataIfNeeded()` caller waits
until the sync run ends — `PrepareImportUseCaseImpl.planSongs` (`songRepository.loadSongsIfNeeded()`),
`ExportLibraryUseCaseImpl`, and for the setlist repository `SetlistRepositoryImpl.latest()` (line 151), which is on
the path of every setlist edit (add to setlist, reorder, archive) if the setlist rescan is the one looping.

## Fix
Bound the re-read and cover the remainder by replaying the changes, which every `updateData` caller in the codebase
makes safe: each transform is "replace/drop the entries of these file names with what is on disk now"
(`SongRepositoryImpl` / `SetlistRepositoryImpl`: `saveSong`, `createSong`, `renameSong`, `deleteSong`, `refresh`,
`adoptImported`, `write`, `forget`), so applying them in order onto a fresh listing gives the same list the next
listing would.

1. In `BaseLocalDataRepository`, record every transform `updateData` applies:
   `private val transformsDuringRead = MutableStateFlow<List<(T?) -> T>>(emptyList())`, appended to with
   `transformsDuringRead.update { it + transform }` inside `updateData` (an atomic CAS on every platform, and
   `updateData` is not suspend, so no `Mutex`). This can replace `updateCount`: "did anything land" is
   `transformsDuringRead.value.isNotEmpty()`.
2. `readOnce` clears that flow to `emptyList()` before calling `loadDataFromLocalSource()`.
3. `read()`: do at most `MAX_REREADS` (1 is enough: it covers the one-off save that lands during an ordinary rescan,
   which is the case the current comment describes) re-reads. After the last allowed read, if transforms landed
   during it, take them (`getAndUpdate { emptyList() }`), fold them onto the result in order, and publish that
   (`_dataState.value = DataState.Idle(result)`), instead of reading again.
4. Keep the `Failure`/cancellation handling of `readOnce` as it is; replay only on a successful read.
5. Update the class KDoc (lines 27-31) and `data/repository/implementation/CLAUDE.md` ("A write that lands through
   `updateData` while a read is running makes that read go again once it has published…") to say: once more, then
   the changes that keep landing are applied onto the result.

Tests (`BaseLocalDataRepositoryTest.kt`, commonTest): a fake repository whose `loadDataFromLocalSource` calls
`updateData` on every invocation (simulating a live refresh landing during each read) — assert that
`reloadData()` returns after at most 2 reads and that the published list contains the change of the last update;
keep the existing test that one update during a read causes exactly one re-read.

## Verification
`./gradlew :data:repository:implementation:desktopTest`. Manual (iOS simulator or desktop): connect a Dropbox folder
holding ~1000 songs to an empty library, and while the first sync runs, background the app and bring it back (desktop:
minimise/restore). Expected: the loading indicator clears within one or two scans, and an import started meanwhile
gets to its plan without waiting for the run to end.

## Conflicts
`SyncRepositoryImpl` live refresh is the sync lane's area (not changed here). Any lane touching
`BaseLocalDataRepository` or `SongRepositoryImpl.refresh`.

## Open decision
- A: bounded re-read + replay (recommended; keeps the common single-save case exactly as today).
- B: always replay, never re-read (simpler, but a rescan that raced a sync write would carry the refresh's older read
  of a file until the next live refresh).
- C: leave the loop but let the sync repository skip live refreshes while a rescan is running (couples the two).
