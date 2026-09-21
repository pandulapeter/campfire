# 08 · After a sync run that fails part-way, the lists and the open songs show the library as it was before the run

**Severity:** wrong behaviour with a path to data loss (all platforms; every run that loses the network after it has
moved files — the ordinary failure) · **Area:** `:data:repository:implementation` (`SyncRepositoryImpl`)

## Symptom
1. Two devices. On the tablet, add three songs to `gig.setlist.json` and edit the text of a song; sync.
2. On the phone, start a run and lose the network part-way (walk out of Wi-Fi range, or turn on flight mode once
   the progress bar moves). Settings reports "could not reach the service".
3. The files the run had already brought down are on disk, but the phone still lists the old setlist, does not list
   downloaded songs, still lists songs the run deleted (opening one fails), and the edited song opens with its **old
   text**, from the text cache.
4. Tap a transposition in that setlist, or save that song in the editor: the stale version is written over the one
   sync had just brought in, the file now differs from the index, and the next successful run uploads it — the
   tablet's three songs, or its edit, are gone on every device.

It stays that way until a later run completes *with changes*, an import, a manual refresh or a restart.

## Cause
`runSynchronization` in
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
tells the song and setlist repositories to read the library again on two of its three ways out. `Completed` does
(`:290-292`, when `summary.hasChanges`), the cancellation branch does (`:305-309`), the failure branch does not
(`:312-316`):

```kotlin
} catch (exception: Exception) {
    println("The sync run failed: ${exception.message}")
    indexWriteJob?.cancelAndJoin()
    latestIndex?.let { saveIndex(it.copy(isRunInProgress = false)) }
    updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason())) }
}
```

The only thing that told the repositories anything during the run is the live rescan (`:334-340`), which runs at
most once a second and is skipped while the previous one is going, so whatever finished after the last of them —
several files, at six transfers at a time — is in neither cache. `SetlistRepositoryImpl.updateSetlist` (`:53-55`)
builds its write on the cached setlist, and `SongContentRepositoryImpl`'s text cache is only cleared by `rescan()`.

Verified against `29820b93`. One correction to the reports: R3 offers `latestIndex != null` as the proxy for "did
anything move", but `latestIndex` is assigned before the engine is even called (`:253`), so it is always non-null
in that branch. An unconditional rescan is not right either: the commonest failure of all is an app started
offline, where `provider.list()` throws before anything has moved, and re-reading a library of thousands on every
offline launch is exactly what the comment at `:288-289` says a run must not cost.

## Fix
All in `SyncRepositoryImpl.kt`, written against the file as plans 01 and 06 leave it (06 has introduced
`saveIndexQuietly`). Plan 07 stops the Dropbox provider from relabelling a cancellation; it deliberately leaves
these two catch blocks alone and refers here, so the two plans can land in either order.

This section is the **one statement of the final shape** of the two catch blocks; plan 06 refers here.

1. The two branches that end a run early owe the same three things, so they share them. New private function, next
   to `rescanLibrary`:

   ```kotlin
   /**
    * What a run that did not reach its end still owes: the periodic writer out of the way so it cannot land after the
    * final write, the index as far as the run got without the marker saying one is going, and - where files may have
    * moved - the lists told about them. Without that last step the repositories keep the library from before the
    * run in their caches, and the next change made to a cached setlist or song text writes the old version back over
    * the one the run brought in.
    *
    * Always called inside [NonCancellable]: a stopped run is cancelled by definition, and a failed one may be - a
    * provider's exception can win over the cancellation that caused it - and in a cancelled coroutine the first
    * suspending call here would throw and skip the rest.
    */
   private suspend fun finishRunCutShort(latestIndex: SyncIndexDocument?, hasFinishedOperations: Boolean) {
       indexWriteJob?.cancelAndJoin()
       latestIndex?.let { saveIndexQuietly(it.copy(isRunInProgress = false)) }
       // Only when an operation got as far as finishing: a run that fails on its listing - every launch without a
       // network - has moved nothing, and reading a whole library again for it would cost more than the run did.
       if (hasFinishedOperations) {
           rescanLibrary()
       }
   }
   ```

2. In `runSynchronization`, next to `var latestIndex`:

   ```kotlin
   var hasFinishedOperations = false
   ```

   and in the `onIndexChanged` lambda handed to the engine, which the engine calls once per finished operation:

   ```kotlin
   onIndexChanged = {
       latestIndex = it
       hasFinishedOperations = true
       scheduleIndexWrite(it)
   },
   ```

   (No extra synchronisation: the lambda runs under the engine's results lock, and the catch blocks only run once
   every operation has completed or been cancelled — the same guarantee `latestIndex` already relies on.)

3. The two catch blocks become, in full:

   ```kotlin
   } catch (exception: CancellationException) {
       // Stopped rather than broken: nothing is wrong and nothing needs fixing, so this is its own outcome.
       withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
       updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Interrupted) }
       throw exception
   } catch (exception: Exception) {
       println("The sync run failed: ${exception.message}")
       withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
       updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason())) }
   } finally {
       // The one thing that has to be true on every way out, including any added later: no progress means
       // the settings screen offers to start a run again instead of offering to stop one that is over.
       updateConnected { if (it.progress == null) it else it.copy(progress = null) }
   }
   ```

   The `try` body, the `Completed` branch's own `if (result.summary.hasChanges) rescanLibrary()` and the `finally`
   stay as they are. The cancellation branch gains the same "only if something finished" condition the failure
   branch gets, which is a saving and not a behaviour change: a run stopped while it was still preparing has moved
   nothing.

Do not move the rescan into `finally`: `Completed` and `DeletionsNeedConfirmation` decide about it on their own
terms, and a `finally` cannot tell them apart from a failure.

## Tests
`SyncRepositoryImplTest` (the harness from plan 06). Two additions to the fakes first:

- `FakeSyncProvider`: `var onUpload: (SyncKey) -> Unit = {}`, called at the top of `upload`, mirroring `onDownload`.
- `RecordingSongRepository`: an `onRescan: () -> Unit = {}` constructor parameter called from `rescan()`.

New case `` `a run that fails after moving files reads the library again` ``: the remote holds `song(1)`, the
library holds `song(2)` (new, so it is planned as an upload), the index is empty, and `onUpload` throws
`SyncNetworkException("Offline")`. The download group finishes before the upload group starts, so exactly one file
has moved when the run fails. `onRescan` records a copy of `local.files.keys` each time. Wait for the outcome and
expect `SyncOutcome.Failure(SyncFailureReason.NETWORK)` and the **last** recorded snapshot to contain `song(1)`.
(Before the fix the only rescan is the live one fired by the very first progress event, which sees a library
without it.) Keep such tests to one operation per ordering group: the run is on `Dispatchers.Default` and the
in-memory fakes are not thread safe.

## Verify
- The unit test command, then the three compile checks.
- Desktop, two accounts of the same Dropbox (or the web UI standing in for the second device): put ~40 new songs
  into `Apps/Campfire/songs`, press **Sync now**, and cut the network once the bar is past a third. Settings shows
  the network failure; the Songs screen lists every song that is in `library/songs/` (compare the counts).
  Reconnect and sync: the rest arrive.
- Start the app with the network off and a large library: the failure is reported and no second library scan runs
  (no second flash of the list's loading state; the log shows no re-read).

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet: where it says the repository "is what tells the
song and setlist repositories to rescan afterwards", add "— after a completed run that changed something, and after
a stopped or failed one in which any operation had finished (`finishRunCutShort`, always under `NonCancellable`),
since files that moved before the run ended are on disk whichever way it ended."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
06 (`saveIndexQuietly`, the test harness). Independent of 07, which touches other parts of the same file: with 07
in place a stopped run reaches the cancellation branch at all, and with this plan in place the failure branch it
reaches until then cleans up properly as well.
