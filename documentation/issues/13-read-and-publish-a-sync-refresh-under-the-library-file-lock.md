# Read and publish a sync refresh under the library file lock, so it cannot put back what a save or deletion replaced

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`,
`data/repository/implementation/CLAUDE.md`

## Problem

`refresh(fileNames)` — what `SyncRepositoryImpl.refreshChangedFiles` calls during a run (the live refresh) and when
it ends — reads the files outside `LibraryFileLock` and only afterwards replaces their cache entries with what it read
(`SongRepositoryImpl.kt:58-66` at 8ee010b36):

```kotlin
override suspend fun refresh(fileNames: Set<String>) {
    if (fileNames.isEmpty()) return
    if (songs.first().data == null) return rescan()
    val reloaded = fileNames.chunked(REFRESH_BATCH_SIZE).flatMap { batch ->
        coroutineScope { batch.map { fileName -> async { loadSongOrNull(fileName) } }.awaitAll() }
    }.filterNotNull()
    songContentRepository.invalidate(fileNames)
    updateData { current -> current.orEmpty().filterNot { it.fileName in fileNames } + reloaded }
}
```

`SetlistRepositoryImpl.refresh` (`:72-86`) has the same shape. Every write (`saveSong`, `deleteSong`, `createSong`,
`renameSong`, and the setlist repository's `writing { }` changes) writes the file and updates the cache as one step
under `LibraryFileLock`, but a refresh's `updateData` lands whenever its reads are done. On a first sync a refresh
reads hundreds of files, so the gap between reading song S and publishing it lasts seconds:

1. The run downloads S; S is in `changedFiles`.
2. A live refresh reads S (version D).
3. While it reads the other files, the user toggles a tag on S from its card: `saveSong` writes E and puts E in the
   cache under the lock.
4. The refresh's `updateData` replaces S's entry with D. The card shows the tag change undone while the file holds E,
   and nothing reads S again until the run changes it or a rescan happens.

With `deleteSong` in step 3 the deleted song comes back in the list and opening it finds no file; setlists behave the
same with `updateSetlist` / `deleteSetlist`. The module CLAUDE.md's promise that "each change replaces or drops the
entries of the files it names with what is on disk now" does not hold for a refresh, which carries what *was* on disk.

## Fix

Recommended: hold `libraryFileLock` from the first read of a refresh to its `updateData`, in both repositories:

```kotlin
override suspend fun refresh(fileNames: Set<String>) {
    if (fileNames.isEmpty()) return
    if (songs.first().data == null) return rescan()
    // Under the lock every write holds from its write to its cache update, so that a save or a deletion lands either
    // before these reads, which then see it, or after the list has been updated with them - never between, where the
    // version read here would be put back over it.
    libraryFileLock.withLock {
        val reloaded = ... // unchanged
        songContentRepository.invalidate(fileNames)
        updateData { ... } // unchanged
    }
}
```

`SetlistRepositoryImpl.refresh` gets the same wrapping (the class already holds `libraryFileLock`, which `writing`
takes). Lock order stays safe: the writes take their own mutex (`nameMutex`, the
setlist lock) first and `libraryFileLock` inside it, and the refresh takes `libraryFileLock` alone; nothing inside the
refresh takes another lock, and the lock's "only around local reads, never a request" rule holds. Callers of
`refresh` (`SyncRepositoryImpl.refreshChangedFiles`) do not hold the lock. The cost is that the engine's writes and a
user's save wait for one refresh's reads — local reads only, and the live refresh already runs at most a sixth of the
time (`liveRescanPauseAfter`); the list still gets one update per refresh.

Alternative, if that wait proves noticeable on the web's OPFS: take the lock per batch of `REFRESH_BATCH_SIZE`,
reading and publishing each batch under it (one `updateData` per batch — more list rebuilds downstream). A third
option is a replay in `BaseLocalDataRepository` (record the `updateData` transforms made while a refresh reads and
apply them again after its own, inside the same atomic update, as `publishPartialData` does for a full read); it
needs no lock but is the most code. The recommended default is the whole-refresh lock.

In `data/repository/implementation/CLAUDE.md`, where `refresh(fileNames)` is described ("reads the named files alone,
for a sync run that changed them…"), add that it reads and updates the list under `LibraryFileLock`, so that a save
or deletion of one of those files is never undone by it.

## Tests

`SongRepositoryImplTest`: `a save made while a refresh reads is not undone by it` — a fake `SongLocalSource` whose
`loadSong` for `b.cho` suspends on a `CompletableDeferred`; start `refresh(setOf("a.cho", "b.cho"))` in a
`launch`, let it suspend, launch `saveSong` for `a.cho` with new text, complete the deferred, join both → the cached
`a.cho` is the saved version. Same for `deleteSong` (the song stays gone). `SetlistRepositoryImplTest`: the same with
`updateSetlist` during a setlist refresh. (Without the fix the save completes before the refresh publishes and the
assertion fails; with it the save waits for the lock.)

Run `./gradlew :data:repository:implementation:desktopTest`.

## Manual check

Connect Dropbox on a device with a large library in the folder and an empty library here, start the first sync, and
while songs are arriving add a tag to one of the songs that has already appeared (from its card's menu), then delete
another one. Neither change is undone in the list while the run carries on or when it ends.
