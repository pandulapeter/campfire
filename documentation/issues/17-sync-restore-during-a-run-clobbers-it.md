# 17 · Reopening the app on Android while a sync is going makes it look interrupted, stops its notification and clears its marker

**Severity:** wrong behaviour (Android; every time the app is swiped away and reopened during a long run — the
case the foreground service exists for) · **Area:** `:data:repository:implementation`
(`SyncRepositoryImpl.restore`), `:data:repository:api` (KDoc)

## Symptom
1. Android, a long first sync is going. Swipe the app away from the recents screen: the foreground service keeps the
   process, and the run, alive.
2. Open the app again. Settings says "The last sync was stopped before it finished" and offers **Sync now** —
   which does nothing, because the run is in fact still going. The sync notification disappears.
3. With the next finished file the progress row comes back, the "interrupted" text still attached to it until the
   run ends.
4. If the process is killed before the next periodic index write, the run *is* interrupted — and the next launch
   does not say so, because step 2 cleared the marker that would have.

## Cause
`restore()` assumes it runs once per process
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:103-152`).
On Android it runs once per **activity**: `CampfireViewModel`'s `init`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:631-650`) calls
`restoreSync()`, and a new activity gets a new ViewModel while the `SyncRepositoryImpl` singleton, its scope and its
run live on. The second `restore()` knows nothing of the run:

```kotlin
val document = loadIndex()                       // says isRunInProgress = true, because one *is*
_syncState.update {
    SyncState.Connected(
        account = account,
        progress = null,                         // the run's progress is thrown away
        lastSyncedAt = document.lastSyncedAt.takeIf { at -> at > 0 },
        lastOutcome = if (document.isRunInProgress) SyncOutcome.Interrupted else null,
    )
}
if (document.isRunInProgress) {
    saveIndex(document.copy(isRunInProgress = false))   // a snapshot from a network round trip ago, over the writer's
}
```

`CampfireSyncService` (`app/android/.../CampfireSyncService.kt:75-91`) watches the same state, sees
`progress == null` and stops itself. The stale document also overwrites whatever the periodic writer wrote while
`loadAccount()` was on the network.

Beyond the reviewer's scenario: two `restore()` calls can also overlap (the first still waiting on
`loadAccount()` when the activity is recreated), and the later one to finish then replaces a state in which the
first one's launch run has already started.

## Fix
`SyncRepositoryImpl.kt`, written against the file as plan 06 leaves it (`saveIndexQuietly` in `restore`). Plan 19
(another lane) reworks the *first* restore — publishing the stored account before the network answers; the two
changes below wrap around whatever body it leaves.

1. One restore at a time. Add next to `mutex`:

   ```kotlin
   /**
    * Start up is asked for once per ViewModel, which on Android is once per activity rather than once per process,
    * and the second one may arrive while the first is still waiting for the service to say whose account this is.
    */
   private val restoreMutex = Mutex()
   ```

   rename the existing function body to `private suspend fun restoreConnection(): SyncRepository.RestoreResult`
   (unchanged inside, so its early `return`s keep working) and make the override
   `override suspend fun restore() = restoreMutex.withLock { restoreConnection() }`.

2. In `restoreConnection`, directly after the `consumePendingRedirect()` block and before
   `providers.firstOrNull { it.isConnected() }`:

   ```kotlin
   // Already answered in this process. The connection, a run that may be going and whatever the last one ended in
   // all live in the state, and reading them again from the disk would replace them with what the index said when
   // that run started - "a run is going", which read at start up means "a run was interrupted".
   (_syncState.value as? SyncState.Connected)?.let { connected ->
       return SyncRepository.RestoreResult(
           isConnected = true,
           didReturnFromAuthorization = false,
           wasInterrupted = !connected.isSyncing && connected.lastOutcome == SyncOutcome.Interrupted,
       )
   }
   ```

   `RestoreSyncUseCase` then calls `synchronize()`, which is already a no-op while a run is going and starts the
   ordinary "the app was opened" run when none is. `wasInterrupted` is answered from the state rather than as a
   flat `false` so that reopening the activity does not start a run over an "interrupted" message nobody has read
   yet — the same reason the first restore reports it.

   The reviewer's `syncJob?.isActive == true ||` is left out as redundant: a run only ever exists while the state
   is `Connected` (`disconnect()` cancels and joins it before it changes the state).

   Do not early-return for `Connecting`, `ConnectionFailed` or `Disconnected`: those are cheap to work out again,
   and a second look is how a connection made in the meantime is found.

3. `data/repository/api/.../SyncRepository.kt`, KDoc of `restore()`, append: "May be called more than once in a
   process - on Android every new activity's ViewModel does. Only a call that finds no connection in [syncState]
   reads anything; a later one answers from the state and never touches a run that is going."

## Tests
`SyncRepositoryImplTest` (harness from plan 06). `FakeSyncProvider.onDownload` becomes
`var onDownload: suspend (SyncKey) -> Unit = {}` so that a test can hold a run open without blocking a thread
(every existing lambda assigned to it still compiles); the constructor parameter changes type with it.

- `` `restoring again while a run is going leaves the run alone` `` — one remote song, `onDownload = { gate.await() }`
  with a `CompletableDeferred<Unit>`. `restore()`, `synchronize(ASK)`, wait for
  `syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }`, then `restore()` again. Expect
  `isConnected`, `!wasInterrupted`; the state still `isSyncing` with `lastOutcome == null`; the stored index still
  `"isRunInProgress": true`. Then `gate.complete(Unit)` and wait for `SyncOutcome.Success`.
- `` `restoring again after an interrupted run still reports it` `` — stored index with `isRunInProgress = true`;
  the first `restore()` answers `wasInterrupted = true`, and so does the second, with
  `lastOutcome == SyncOutcome.Interrupted` still in the state.
- `` `restoring again with nothing going starts from the state` `` — after a completed run, a second `restore()`
  answers `isConnected = true`, `wasInterrupted = false`, and `lastOutcome` is still the `Success`.

## Verify
- The unit test command, `:app:android:assembleDebug`.
- Android emulator with a Dropbox key and a library of a few hundred songs on the remote side only: start the app,
  let the run begin, swipe the app away from recents (the notification stays and keeps counting), open the app
  again. Settings shows the progress row and **Stop syncing**, never the interrupted text; the notification is
  still there; the run completes and the date appears.
- The marker still works: during another run, `adb shell am force-stop com.pandulapeter.campfire.debug` (a plain
  `am kill` does not end a process that holds a foreground service), then start the app. "The last sync was stopped
  before it finished" is shown and no run starts on its own.

## Docs
`data/repository/implementation/CLAUDE.md`, `sync/` bullet, after the sentence about `isRunInProgress` being found
at `restore`: add "`restore` is asked once per ViewModel — on Android once per activity — so a call that finds the
state already `Connected` answers from it and reads nothing: read again from the disk, a run that is going would
look like one that was interrupted, and its marker would be cleared under it."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
06 (test harness, `saveIndexQuietly` in `restore`). Shares `restore()` with plan 19 and with plan 11's Android
redirect; neither changes what this plan adds, but they must not land at the same time.
