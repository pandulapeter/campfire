# 24 · Five small sync fixes: a late index write on the web, wall-clock throttles, names differing by case, stop-then-disconnect, the service timeout

**Severity:** minor (each item names its platforms and how likely it is) · **Area:**
`:data:repository:implementation` (`SyncRepositoryImpl`, `SyncEngine`), `:app:android` (`CampfireSyncService`)

Five independent items, one section each; every section has its own symptom, cause and fix, and the shared
sections at the end cover all of them. They are one plan because each is a few lines in files the sync lane has
open anyway. Written against the code as plans 01–23 leave it.

## Symptom
See each item.

## Cause
See each item.

## Fix

### A. Web: a cancelled periodic index write can land after the final one
**(web; low confidence, narrow window.)**

*Symptom.* A run ends on the web build and, rarely, the next page load says "The last sync was stopped before it
finished" although it completed, and the index has lost the run's last files.

*Cause.* Before its final index write a run does `indexWriteJob?.cancelAndJoin()` (`SyncRepositoryImpl.kt:271`,
and in plan 08's `finishRunCutShort`). On the web, cancelling only cancels the Kotlin `await`: the JS chain in
`FileStorage.wasmJs.kt:198-205` (`createWritable().then(write).then(close)`) carries on, `createWritable` allows
several writers at once, and the last `close()` wins. If the older chain closes after the final write, the file ends
up holding the older snapshot with `isRunInProgress = true`.

*Fix.* Wait for the periodic writer instead of cancelling it — at both call sites `indexWriteJob?.cancelAndJoin()`
becomes `indexWriteJob?.join()`. It costs at most one write of a few hundred kilobytes, and on the other three
platforms a write that has started is not interrupted by a cancellation either, so nothing is lost there. Since
plan 06 that job cannot fail. Replace the clause in `finishRunCutShort`'s KDoc with "the periodic writer waited for,
so that it cannot land after the final write — waited for rather than cancelled, because on the web a cancelled write
carries on in the browser". Remove the `cancelAndJoin` import only if `disconnect()` and plan 23's
`rescanLibraryAfterRun` no longer use it (they do).

### B. The index write throttle follows the wall clock
**(all platforms; only when the clock is set back during a session.)**

*Symptom.* The device clock jumps back (time zone travel with manual time, NTP correcting a wrong clock) while the
app is running: no periodic index write happens until the clock has caught up, so a run killed in that window
loses everything it transferred.

*Cause.* `scheduleIndexWrite` compares `Clock.System.now()` with the time of the last write
(`SyncRepositoryImpl.kt:348-354`). (The live rescan had the same shape; plan 23 has already moved it to
`TimeSource.Monotonic`.)

*Fix.* The same shape plan 23 gave the live rescan:

```kotlin
private var indexWriteJob: Job? = null
private var lastIndexWrite: TimeMark? = null
```

```kotlin
private fun scheduleIndexWrite(snapshot: () -> SyncIndexDocument) {
    if (indexWriteJob?.isActive == true) return
    if (lastIndexWrite?.let { it.elapsedNow() < INDEX_WRITE_INTERVAL } == true) return
    lastIndexWrite = TimeSource.Monotonic.markNow()
    // Taken here and not in the job: the engine's lock is what makes reading its map safe, and it is only held
    // for as long as this call runs.
    val document = snapshot()
    indexWriteJob = scope.launch { saveIndexQuietly(document) }
}
```

with `INDEX_WRITE_INTERVAL_MS = 2000L` in the companion becoming `val INDEX_WRITE_INTERVAL = 2.seconds` (same
KDoc). `Clock.System` stays where a point in time is what is wanted: `syncedAt` in the `Completed` branch. The first
write of a process is still never throttled, which `SyncRepositoryImplTest` relies on.

### C. A local file whose name differs from another's only by case is retried for ever, silently, at the cost of a second pass
**(Android, Linux, web — file systems with exact names — and only for names made by hand; medium confidence.)**

*Symptom.* The library holds `Song.cho` and `song.cho` (copied in by hand on the desktop, or imported before the
names were normalized). One of them never reaches Dropbox, nothing says so, and every run — every launch — lists
both sides twice.

*Cause.* Dropbox paths are case-insensitive. `foldRemoteNamesOntoLocal` maps the remote file onto the local key
that matches it exactly, and the other local file is planned as a new upload (`expectedRevision = null`), which
Dropbox refuses as a conflict because the path exists. `upload` (`SyncEngine.kt:314`) reports
`hasUnresolvedConflict`, which buys a second pass in which exactly the same thing happens, and after
`MAXIMUM_PASSES` the run completes without a word. Plan 16 does not catch it either: a conflict is not a failure.

*Fix.* Keep what the service decides (on a service with exact names both files sync, and nothing below changes
that), but recognise the one conflict that another pass cannot settle and report it through plan 16's
`SyncSummary.failed`.

1. In `SyncEngine.synchronize`, after `readLocalStates()`:

   ```kotlin
   // Names a service that ignores case takes for one file. Only ever consulted once such a service has refused
   // one of them, so a service with exact names never notices.
   val caseCollisions = local.groupBy { it.key.folded() }.values
       .filter { it.size > 1 }
       .flatten()
       .mapTo(mutableSetOf()) { it.key }
   ```

   and hand it down next to `remoteFiles`: `apply(…, caseCollisions = caseCollisions)` →
   `runOperation(…, caseCollisions)` → `upload(provider, operation, caseCollisions)`; `deleteLocally` passes it on
   to the `upload` it calls.

2. In `upload`, the conflict branch:

   ```kotlin
   RemoteWriteResult.Conflict -> if (operation.expectedRevision == null && key in caseCollisions) {
       // Refused because the service already holds this name in another spelling, which is the other local file.
       // Another pass would be refused the same way, so this is a file that could not be synced rather than a
       // conflict waiting to be resolved.
       println("Could not sync \"${key.path}\": the service holds the same name in another case.")
       OperationOutcome(summary = SyncSummary(failed = listOf(key.name)))
   } else {
       // The remote file moved under the write, which asks for another pass over a fresh listing.
       OperationOutcome(hasUnresolvedConflict = true)
   }
   ```

   Extend the KDoc of `foldRemoteNamesOntoLocal` with: "Two *local* names that differ only by case cannot both
   exist on such a service; the one it refuses is reported as a file that could not be synced, see `upload`."

Settings then says "One file could not be synced…: Song.cho", which is something the user can act on by renaming
it. Do not rename or merge the file on the user's behalf: which of the two is the song is not the engine's call.

### D. Disconnect right after Stop can leave an index behind
**(all platforms; harmless today, but the comment in `disconnect()` promises otherwise.)**

*Symptom.* None a user would see today: the index left behind is accurate and belongs to the account that was just
disconnected, and reconnecting the same account makes use of it. Connecting a *different* account resets it
(`completePendingAuthorization`). It is fixed because the next change to that code will trust the comment.

*Cause.* `cancelSynchronization()` nulls `syncJob` (`SyncRepositoryImpl.kt:230-233`), so a `disconnect()` that
follows within the stopped run's clean-up has nothing to `cancelAndJoin()`, and the run's `NonCancellable` index
write can land after `saveSyncIndex(null)` (`:212`). The same happens with Stop, **Sync now**, Disconnect: `syncJob`
is then the new run, waiting for the lock, while the old one is still writing.

*Fix.* A run holds `mutex` from its first line to the end of its clean-up, so that lock — not the job reference —
is what says "no run is writing any more". In `disconnect()`:

```kotlin
// The index describes a remote folder this device is no longer looking at. Kept, and it would read that
// folder's every file as a deletion the next time something connects. Under the run lock, because a run that
// was stopped a moment ago is not in syncJob any more and may still be writing the index on its way out; no new
// one can slip in, since the providers above no longer say they are connected.
mutex.withLock {
    syncStateLocalSource.saveSyncIndex(null)
    _syncState.update { SyncState.Disconnected }
}
```

`cancelSynchronization()` stays as it is.

### E. Android: the foreground service does not answer `onTimeout`
**(Android 15+; not reachable by any path found, cheap insurance.)**

*Symptom.* None observed. Android 15 gives a `dataSync` foreground service six hours in any twenty-four while the
app is in the background; when they are used up it calls `Service.onTimeout(int, int)`, and a service still in
the foreground a few seconds later is stopped with an exception that takes the process down. Every run is started
from the foreground and the budget is reset whenever the app comes forward, so getting there takes a run of more
than six hours in the background.

*Cause.* `app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt` does not override it
(target SDK 37).

*Fix.* Next to `onDestroy`:

```kotlin
/**
 * Android allows a data sync service six hours of background time in a day, and takes the app down with a service
 * that is still in the foreground a few seconds after being told they are up. The run is stopped rather than left
 * to carry on without the service: stopped, it writes its index and says it was interrupted, while left alone it
 * ends whenever the system freezes a process it no longer has a reason to keep.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
override fun onTimeout(startId: Int, fgsType: Int) {
    KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke()
    stop()
}
```

(`androidx.annotation.RequiresApi`; no `super` call is needed, the platform's implementation is empty. On older
versions the method is simply never called.)

## Tests
- A and B: none possible (a browser's write queue, a clock); the existing `SyncRepositoryImplTest` cases keep
  pinning that the final index is the complete one and that the first periodic write is not throttled.
- C: `FakeSyncProvider` gets `private val ignoresCase: Boolean = false` (an `upload` with `expectedRevision == null`
  answers `Conflict` when any held key has the same kind and the same lowercased name) and `var listCount = 0`,
  incremented by `list()`. `SyncEngineTest`, new case
  `` `a local name another one shadows on a service that ignores case is reported instead of retried` ``: library
  `song("Song")` and `song("song")` with different texts, remote `song("song")` in step with its index entry,
  `ignoresCase = true`. Expect `Completed`, `summary.failed == listOf("Song.cho")`, `provider.listCount == 1`, and
  the remote still holding one file. A second case without `ignoresCase`: both upload, nothing failed.
- D: `SyncRepositoryImplTest`, `` `disconnecting right after stopping a run leaves no index behind` `` — hold a run
  open with plan 17's suspending `onDownload = { gate.await() }`, wait for progress, `cancelSynchronization()`, then
  `disconnect()` at once. Expect `FakeSyncStateLocalSource.index == null` when `disconnect()` returns and the state
  `Disconnected`. (Before the fix the stopped run's index write lands afterwards, on most runs.)
- E: none (`:app:android` is untested).

## Verify
- The unit test command; `:app:android:assembleDebug` (E), `:app:web:wasmJsBrowserDistribution` (A).
- C by hand needs a file system with exact names: Android emulator, `adb push` two files `Song.cho` / `song.cho`
  into the debug build's `files/library/songs` (`run-as`), sync: Settings names the one that could not be synced.
- D: on the desktop build press **Stop syncing** during a run and **Disconnect** immediately; afterwards
  `preferences/sync-index.json` does not exist.
- E cannot be reached by hand in reasonable time; `adb shell cmd activity` has no switch for it on a release
  system image. The compile check is what there is.

## Docs
- `data/repository/implementation/CLAUDE.md`, `sync/` bullet: "the periodic writer cancelled and joined first"
  becomes "the periodic writer waited for first (not cancelled: on the web a cancelled write carries on in the
  browser)"; and to "Disconnecting cancels a run that is still going and waits for it" add "and deletes the index
  under the run lock, so that a run stopped a moment earlier has finished writing it".
- `app/android/CLAUDE.md`, the `sync/CampfireSyncService` paragraph: add "It answers `onTimeout` — Android 15's
  six-hour budget for data sync services — by stopping the run and itself."
- C needs no doc change beyond the KDoc in its step 2.

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt`
- `data/repository/implementation/CLAUDE.md`
- `app/android/CLAUDE.md`

## Depends on
06 (`saveIndexQuietly`, the test harness), 08 (`finishRunCutShort`), 16 (`SyncSummary.failed`, which item C
reports through), 17 (the suspending `onDownload` hook), 23 (the shape of `scheduleIndexWrite` item B rewrites).
Items A, D and E stand on their own if any of those is dropped.
