# 01 · A stopped or failed sync run forgets every file it already transferred

**Severity:** high (needless conflict copies, lost edits on the next run) · **Area:** `:data:repository:implementation`

## Symptom

Start a first sync of a few hundred songs, stop it (or lose the network) after most of them have come down. The next
run plans every one of those files as `Resolve` (present on both sides, no index entry). The ones nobody touched
reconcile by content hash, but any downloaded song the user edited in between gets a ` (2)` copy of the version they
were editing from, and the summary reports conflicts that were never real.

## Cause

`SyncEngine.apply()` (`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`,
`apply`, around lines 125–157) merges every finished operation into the local `updated` map and only returns it in
`PassOutcome` when the whole pass completes normally. A `CancellationException` or a `SyncNetworkException` from any
one operation unwinds `coroutineScope` and the map is gone. `SyncRepositoryImpl.runSynchronization()` then calls
`clearRunInProgress()`, which re-saves the **old** index (`loadIndex().copy(isRunInProgress = false)`).

The module doc says "the index is only told about a file once that file has actually moved, so anything half done
simply looks unsynced next time" — in practice nothing from an interrupted pass is told.

## Fix

1. **Let the engine report the index as it grows.** Add a parameter to `SyncEngine.synchronize`:

   ```kotlin
   onIndexChanged: suspend (SyncIndexDocument) -> Unit,
   ```

   Inside `apply`, after the `results.withLock { … }` merge block, build a snapshot and hand it out:

   ```kotlin
   onIndexChanged(SyncIndexDocument.of(providerId, accountId, document.lastSyncedAt, updated.toMap()).copy(isRunInProgress = true))
   ```

   `apply` needs `providerId`, `accountId` and `document.lastSyncedAt` for that; pass them down from `synchronize`.
   Copying the map once per finished operation is O(n) per operation; acceptable for a library of thousands, but see
   step 3 for the throttle that keeps the disk writes down.

2. **Keep the latest snapshot in the repository.** In `SyncRepositoryImpl.runSynchronization()`, declare
   `var latestIndex: SyncIndexDocument = document.copy(isRunInProgress = true)` before the `try`, and pass
   `onIndexChanged = { latestIndex = it; scheduleIndexWrite(it) }` to the engine.

3. **Write it periodically, and always on the way out.** Add `scheduleIndexWrite` next to `scheduleLiveRescan`, with
   the same throttle shape (`indexWriteJob`, `lastIndexWriteAt`, `INDEX_WRITE_INTERVAL_MS = 2000L`), writing through
   `saveIndex` on `scope`. Then in both the `CancellationException` and the `Exception` branches replace
   `clearRunInProgress()` with:

   ```kotlin
   withContext(NonCancellable) {
       indexWriteJob?.cancelAndJoin()
       saveIndex(latestIndex.copy(isRunInProgress = false))
       rescanLibrary()
   }
   ```

   (The `Exception` branch does not need `NonCancellable`, but it needs the same two lines.) The success path is
   unchanged: it writes `result.index.copy(lastSyncedAt = syncedAt)`, which is the full, final index. Make sure the
   periodic writer cannot land *after* the final write: cancel-and-join it before the final `saveIndex` on every path.

4. **`lastSyncedAt` stays what it was** on an interrupted run; only a completed run moves it. The snapshot in step 1
   already carries `document.lastSyncedAt`.

5. **Docs.** Update the `SyncEngine` class KDoc and `data/repository/implementation/CLAUDE.md` so they say the index
   is written as the run goes, and that an interrupted run keeps what it transferred.

## Verification

- Extend `SyncPlannerTest` neighbours with an engine test if one can be written against a fake `SyncProvider` and
  `LibraryFileLocalSource` (both are interfaces): a provider whose third download throws `SyncNetworkException` must
  leave the first two keys in the index handed to `onIndexChanged`.
- Manual: desktop, connect Dropbox, start a first sync of 50+ songs, press **Stop syncing** halfway, open
  `preferences/sync-index.json`: the downloaded songs must be listed and `isRunInProgress` must be `false`. Run again:
  no ` (2)` copies, summary shows only the remaining files.
- Unit tests green.
