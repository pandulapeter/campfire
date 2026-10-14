<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Finish a sync run's local write and its library notice together, even when the run is stopped

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (worst on the web)
**Challenged:** sound
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncEngine.kt`, `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/ConflictResolver.kt`, a new test in
`data/sync/implementation/src/commonTest/.../run/` (or `TwoDeviceSyncTest.kt`), `FakeLibraryFileLocalSource.kt` if the test
needs a hook to cancel inside a write, `data/sync/implementation/CLAUDE.md` (one sentence)

## Problem

Reviewed at `6c3043996`. The engine writes or deletes a library file and only then tells the run about it, inside a
cancellable coroutine:

```kotlin
// SyncEngine.kt, download (≈ line 385) — and the local delete (≈ line 423) has the same shape
libraryFileLock.withLock {
    ...
    libraryFileLocalSource.writeLibraryFile(key.kind, key.name, downloaded.bytes)
    pass.run.onLocalFileChanged(key)
```

```kotlin
// ConflictResolver.kt, the conflict copy (≈ line 95)
val copyName = libraryFileLock.withLock { libraryFileLocalSource.writeLibraryFileToFreeName(...) }
val copyKey = SyncKey(kind = key.kind, name = copyName)
onLocalFileChanged(copyKey)
```

`takeRemote` (≈ line 152) and `discardCopy` (≈ line 176) write or delete and then call `onLocalFileChanged` the same way.

File storage writes are `withContext(Dispatchers.IO)` on the JVM and iOS (`JvmFileStorage`, `IosFileStorage`) and an
awaited promise on OPFS. A cancellation (Stop in Settings, Delete library, desktop exit) that lands while the blocking
write runs lets the write finish on disk, but `withContext` then throws `CancellationException`. `onLocalFileChanged`
is skipped, so `SyncLibraryRefresher` never records the key and `finishRunCutShort` → `refreshAfterRun` does not
re-read it. The cancellation path (`SyncRunner`'s `catch (exception: CancellationException)`) does no full rescan,
unlike the `Throwable` path.

Result: a downloaded `x.cho` holds the other device's text on disk while `SongRepository` and the
`SongContentRepository` LRU keep the old one until the next rescan (days on the desktop); a conflict copy `x_2.cho`
exists on disk but not in the list; a locally deleted song stays listed. The next edit to the cached song writes the
old text back over the downloaded one (the index was not advanced, so the next sync makes a conflict copy rather than
losing it silently).

On the web the cancellation resumes the coroutine while the browser's write is still in flight, so `libraryFileLock`
is released early: a user save of the same file can race the stale write, and `DeleteLibraryUseCaseImpl` (which calls
`cancelSynchronization()` without joining and then deletes everything) can be overtaken on the Safari < 26 worker path
by a download already handed to the worker, which then survives Delete library and is uploaded by the deletion's run.

The repositories already guard the same step with `NonCancellable` (`SongRepositoryImpl`'s `writing`/`naming`).

## Fix

Inside each `libraryFileLock.withLock { … }` above, wrap "write or delete + `onLocalFileChanged`" in
`withContext(NonCancellable) { … }`, so the lock is held until the platform write has settled and the run always hears
about a file it changed. For the conflict copy, move `onLocalFileChanged(copyKey)` inside the lock and the
`NonCancellable` block together with `writeLibraryFileToFreeName` (return the name from the block). The reads that
decide whether to write stay cancellable: only the write and its notice are made atomic.

Do not reorder to "notify before the write": the notice must name a file that exists for the conflict copy, and one
pattern everywhere is easier to keep.

Add one sentence to `data/sync/implementation/CLAUDE.md` where it describes `LibraryFileLock`/writes: a local write and
its `onLocalFileChanged` finish together once started (`NonCancellable`), as the repositories' writes do.

## Tests

In the engine tests, a fake local source whose `writeLibraryFile` suspends on a gate (e.g. a `CompletableDeferred`)
lets the test cancel the run while the write is in progress, then complete the write: assert that the run's refreshed
keys (what `SyncLibraryRefresher` / the test's recording collaborator sees after `finishRunCutShort`) include the
written file, for a download and for a conflict copy. Use `runTest` virtual time.
