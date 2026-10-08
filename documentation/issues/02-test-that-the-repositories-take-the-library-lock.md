# Test that the song and setlist repositories hold the library lock while they write

**Kind:** test  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`,
`.../SetlistRepositoryImplTest.kt`, and possibly `.../SyncEngineTest.kt` in `:data:sync:implementation` (KDoc of `saveUnderTheLock` only)
**Challenged:** amended — there is no `withWriteLocks`: the setlist write helper is the private `writing` (`writeMutex`, then `libraryFileLock`), reached through the public `saveSetlist`; the test steps are spelled out, the fake that records each write is named, and `saveUnderTheLock` already has a KDoc to extend.

## Problem

Two `SyncEngineTest` tests, "a song saved between the last check of its download and the write is not written over"
and "… of its deletion and the deletion is not deleted", used to drive the real repository:

```kotlin
// 2940b0e0a
save = launch { repository.saveSong(SongContent(song(1).name, HERE.decodeToString())) }   // real SongRepositoryImpl(…, lock, …)
```

When d9a03d9ff moved sync into its own module, which cannot see `SongRepositoryImpl`, they began using a stand-in:

```kotlin
save = launch { saveUnderTheLock(lock, local, song(1), HERE) }
```

The engine's half of the contract is still tested. The repository's half (that `saveSong` takes `LibraryFileLock`) is
no longer tested anywhere: `SongRepositoryImplTest` and `SetlistRepositoryImplTest` pass a fresh `LibraryFileLock()`
and never hold it. Production is correct today (`SongRepositoryImpl.saveSong` is
`= libraryFileLock.withLock { … }`, and `deleteSong`, `deleteAllSongs`, import, rename and the setlist writes go
through `libraryFileLock.withLock`). But if a later change drops the lock, CI stays green, and a sync download can then
silently overwrite a song the user just saved, without a conflict copy.

## Fix

In `:data:repository:implementation`'s `commonTest`, add one test per write path that matters to sync, using the
fakes the two files already have (`FakeSongLocalSource.files` in `SongRepositoryImplTest`,
`FakeSetlistLocalSource.saves` / `.files` in `SetlistRepositoryImplTest`). Build the repository with a
`val lock = LibraryFileLock()` of the test's own instead of the inline `LibraryFileLock()`, then, inside `runTest`
(its default `StandardTestDispatcher`):

```kotlin
val release = CompletableDeferred<Unit>()
launch { lock.withLock { release.await() } }
runCurrent()                                   // the test now holds the lock
val write = launch { repository.saveSong(SongContent(FILE_NAME, "saved")) }
runCurrent()
assertEquals("original", localSource.files[FILE_NAME])   // 1. nothing reached the source
release.complete(Unit)
write.join()
assertEquals("saved", localSource.files[FILE_NAME])      // 2. it lands once the lock is free
```

Cover these:
- `SongRepositoryImpl.saveSong` (unguarded, `expectedText` left out, so the write is unconditional);
- `SongRepositoryImpl.deleteSong` (assert the file is still in `files`, then gone);
- `SetlistRepositoryImpl.saveSetlist`, which goes through the private `writing` helper (assert `saves` is empty, then
  holds the setlist).

No deadlock: `LibraryFileLock` is not reentrant, but the test holds it from a separate coroutine, and `writing` takes
`writeMutex` (free) before waiting on `libraryFileLock`, so nothing in the test takes a lock it already holds.

`saveUnderTheLock` in `data/sync/implementation/src/commonTest/.../SyncEngineTest.kt` already has a KDoc ("A save the
way the song and setlist repositories make one: …"); add one sentence to it saying that the repositories' half, that
they take the lock at all, is tested in `SongRepositoryImplTest` and `SetlistRepositoryImplTest`.

## Tests

The tests above. Remove the `withLock` from `saveSong` temporarily to see the new test fail, then restore it. Run
`./gradlew :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest`.

## Manual check

None.
