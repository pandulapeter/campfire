# 06 · With the device's storage full, a sync run crashes the app — on every launch

**Severity:** crash (Android, iOS; certain once storage is full and an account is connected — desktop and the web
only log it) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl`)

## Symptom
The phone is out of space and a Dropbox account is connected.

1. Start the app. `RestoreSyncUseCase` starts the launch run; a few seconds later the app closes.
2. Start it again: Settings says the last sync was interrupted. Press **Sync now**: the app closes again.

It also happens in the middle of an ordinary run, when the downloads themselves fill the disk: the first periodic
write of `sync-index.json` that fails takes the process down. Nothing tells the user that storage is the problem.

## Cause
`SyncRepositoryImpl`
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`)
runs everything in a scope with no exception handler (`:91`):

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

and two of the jobs launched in it can end in an exception, because `saveIndex` throws `LibraryStorageException`
when the file cannot be written (verified: `JvmFileStorage`, `FileStorage.ios.kt` and `FileStorage.wasmJs.kt` all
wrap a failed write in it):

- the periodic writer, with nothing around it (`:348-354`):

  ```kotlin
  indexWriteJob = scope.launch { saveIndex(document) }
  ```

- the run itself. The opening write (`:256`) throws inside the `try`, which is fine — but the
  `catch (exception: Exception)` block that receives it makes the same write again (`:312-316`), and that one throws
  out of the catch block and out of `syncJob = scope.launch { runSynchronization(...) }`:

  ```kotlin
  } catch (exception: Exception) {
      println("The sync run failed: ${exception.message}")
      indexWriteJob?.cancelAndJoin()
      latestIndex?.let { saveIndex(it.copy(isRunInProgress = false)) }
      updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason())) }
  }
  ```

  so `lastOutcome` is never set either. The `CancellationException` branch has the same bare `saveIndex` inside
  `NonCancellable` (`:305-309`).

An uncaught exception in a `launch` with no `CoroutineExceptionHandler` goes to the thread's handler: process death
on Android and on Kotlin/Native, a stack trace on the JVM desktop and in the browser console. There is no
`CoroutineExceptionHandler` anywhere in the repository (`grep` confirms).

## Fix
All in `SyncRepositoryImpl.kt`. The rule: a write of the index that has **somebody to report to** keeps throwing
(the opening write and the `Completed` write are inside the `try`, and the `catch` turns them into
`SyncOutcome.Failure(STORAGE)`); a write made **on the way out or alongside the run** never throws, because the
index is an optimisation — anything it fails to record simply looks unsynced next time.

1. Add next to `saveIndex`:

   ```kotlin
   /**
    * For the writes nobody is waiting on the result of - the periodic one and the ones made on the way out of a run.
    * The index only ever saves work: whatever it fails to record looks unsynced to the next run, which is a slower
    * run and not a wrong one, so a failure here is not worth more than a line in the log. A run whose storage is
    * really gone still says so, through the opening write and the one that completes it.
    */
   private suspend fun saveIndexQuietly(document: SyncIndexDocument) = try {
       saveIndex(document)
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println("Could not write the sync index: ${exception.message}")
   }
   ```

2. Use it at exactly these call sites, and nowhere else:
   - `scheduleIndexWrite`: `indexWriteJob = scope.launch { saveIndexQuietly(document) }`
   - the `DeletionsNeedConfirmation` branch (`:276`) — the question is the true outcome of that run, and a marker
     that could not be cleared only means the next launch reports an interruption;
   - the `CancellationException` branch (`:307`);
   - the `catch (exception: Exception)` branch (`:315`);
   - `restore()` (`:145`), so that a full disk does not cost the caller the `RestoreResult`.

   Leave `saveIndex(document.copy(isRunInProgress = true))` (`:256`), `saveIndex(result.index.copy(...))` (`:287`)
   and the one in `completePendingAuthorization` (`:413`, already inside a `try` that ends in `fail(...)`) as they
   are. Making `saveIndex` itself quiet is the wrong turn: a run on a device that cannot write would then end as
   `Success` with a fresh "last synced" time.

   This plan changes only that one call in the failure branch. The branch is restructured (wrapped in
   `NonCancellable`, given a rescan) by plan 08, which shows the final shape of the whole `try`/`catch`/`finally`;
   do not anticipate that here.

3. Give the scope a last line of defence, and say why in its KDoc:

   ```kotlin
   /**
    * A run belongs to the app, not to whatever screen started it. This repository is a singleton, so a sync
    * carries on while the user moves around the app, and on Android it survives the activity being destroyed -
    * which is what lets a foreground service keep it going after the app has been left.
    *
    * Nothing launched here has anybody to throw to, and an exception that leaves a job with no handler ends the
    * process on Android and iOS. Every job is written not to throw; the handler is there for the one that one day
    * does, because sync is something the app does on the side and must never be what closes it.
    */
   private val scope = CoroutineScope(
       SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
           println("A sync job ended in an exception nothing caught: $throwable")
       },
   )
   ```

   Import `kotlinx.coroutines.CoroutineExceptionHandler` (keep the file's import order as it is). The handler also
   receives `Error`s such as an `OutOfMemoryError`; that is intended — `finally` in `runSynchronization` has already
   cleared the progress by then, and the marker left in the index makes the next launch report an interrupted run.

How the failure reaches the user after this: the opening write throws `LibraryStorageException` →
`catch (exception: Exception)` → the quiet write logs → `exception.toFailureReason()` is `STORAGE` →
`SyncOutcome.Failure(STORAGE)` → Settings shows the existing storage failure text. Mid-run: periodic writes log and
are skipped, per-file download failures are already caught in the engine, and the `Completed` write throws into the
same `catch` with the same result.

## Tests
`:data:repository:implementation` has no test of `SyncRepositoryImpl` yet; this plan adds the harness that plans
08, 17 and 24 reuse.

- `FakeSyncProvider`: add `private val account: SyncAccount? = null` to the constructor and return it from
  `loadAccount()`.
- New file `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
  (MPL header, `internal` classes, a KDoc on each saying what it stands in for):
  - `FakeSyncStateLocalSource(var index: String? = null, var onSaveIndex: (String?) -> Unit = {})` —
    `saveSyncIndex` calls `onSaveIndex(document)` and then stores it, so a test makes a write fail by throwing
    `LibraryStorageException` from the lambda; credentials are a plain `var`.
  - `FakeSyncAuthenticator` — `consumePendingRedirect()` and `prepareRedirectUri()` return null, `authorize`
    throws `UnsupportedOperationException`.
  - `FakePendingAuthorizationStore` — loads null, the other two do nothing.
  - `RecordingSongRepository` and `RecordingSetlistRepository` — `var rescanCount = 0`, incremented by `rescan()`;
    `songs` / `setlists` are `emptyFlow()`; every other member throws `UnsupportedOperationException`.
- New `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`.
  A private `repository(...)` helper builds a `SyncRepositoryImpl` from `SyncProviders(listOf(provider))` and the
  fakes. The run is on `Dispatchers.Default`, so a test waits for it with
  `repository.syncState.first { it is SyncState.Connected && !it.isSyncing && it.lastOutcome != null }` inside
  `runTest` (whose own timeout is what fails a run that never reports).
  - `` `a run whose index cannot be written ends as a storage failure` `` — every `saveSyncIndex` throws
    `LibraryStorageException("Full")`; `restore()`, `synchronize(SyncDeletionPolicy.ASK)`, wait. Expect
    `SyncOutcome.Failure(SyncFailureReason.STORAGE)` and `progress == null`. (Before the fix the exception leaves
    the scope, which `runTest` reports as a failure, and the outcome never arrives.)
  - `` `a periodic index write that fails does not end the run` `` — remote holds one song, local none; the fake
    throws only for a document that is both `"isRunInProgress": true` and names `song_1.cho` (the first periodic
    write, which is never throttled since `lastIndexWriteAt` starts at 0). Expect `SyncOutcome.Success` with
    `downloaded == 1`, and the stored index naming the song with `isRunInProgress` false.

## Verify
- The unit test command, then the three compile checks.
- Android emulator: connect Dropbox, fill the data partition
  (`adb shell fallocate -l <free space> /data/local/tmp/fill` or install until "storage full"), start the app.
  It stays open, and Settings shows the storage failure under the account instead of closing. Delete the filler,
  **Sync now**: the run completes.
- iOS simulator cannot fill a disk conveniently; the compile check plus the shared code path is what stands in.

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet, after "once more on the way out of a stopped or
failed run": add "Those writes and the periodic one never throw (`saveIndexQuietly`): only the write that opens a
run and the one that completes it do, which is how a device that cannot write ends a run as
`SyncFailureReason.STORAGE`. The repository's scope carries a `CoroutineExceptionHandler` that logs, since nothing
launched there has anyone to throw to."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt` (new)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt` (new)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
01 (same lane, shares `FakeSyncProvider.kt`). Plan 08 finishes the failure branch this one touches.
