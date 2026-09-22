# 15 · Leaving the app right after confirming Disconnect leaves the account showing as connected, and "Sync now" then does nothing at all

**Severity:** stuck state (Android mainly — the process outlives the activity; desktop and web lose the state with the
process anyway; iOS rarely finishes the ViewModel; low likelihood, until the next process start or a second Disconnect)
· **Area:** `:data:repository:implementation` (`SyncRepositoryImpl.disconnect`, `runSynchronization`), `:presentation` (`CampfireViewModel.disconnectSyncProvider`)

## Symptom
1. Android, connected account, on a slow network (the disconnect renews an expired token, then revokes it: up to
   2 × 10 s; the token expires after four idle hours, so this is the ordinary case for a disconnect).
2. Settings → Disconnect → confirm, then press Back (from the root, which finishes the activity on Android 11 and
   older; Android 12+ only moves a launcher task to the back) or remove the task from Recents while the process is
   kept.
3. Open Campfire again. Settings shows the account as connected. **Sync now** shows nothing at all — no progress, no
   message. The index of the old account is still on disk. Only a second Disconnect, or a new process, fixes it.

The same dead state follows from 05's offline disconnect on the web, and from any other way the credentials go while
the state says `Connected`.

## Cause
1. The disconnect runs in the ViewModel's scope: `CampfireViewModel.kt:1577-1579`
   (`fun disconnectSyncProvider() = launchLibraryChange { disconnectSyncProvider.invoke() }`), so finishing the activity
   cancels it half way.
2. `SyncRepositoryImpl.kt:263-271` swallows that cancellation:
   ```kotlin
   try {
       provider.disconnect()
   } catch (exception: Exception) {
       println("Could not disconnect from ${provider.id}: ${exception.message}")
   }
   ```
   `DropboxSyncProvider.disconnect` has already cleared the credentials (`DropboxSyncProvider.kt:139`) before the revoke
   (`:143-145`) that the cancellation interrupts. Execution continues into `mutex.withLock { … }` (`:276-279`); the lock
   is taken on its fast path without a cancellation check, and the first suspension inside,
   `syncStateLocalSource.saveSyncIndex(null)` (a `withContext(Dispatchers.IO)`), throws. So: credentials gone, index
   kept, `_syncState` still `Connected`.
3. The next `restore()` answers from that state without looking (`:152-158`, by design since review 2 plan 17), and
   `RestoreSyncUseCase` starts a run, which returns before touching anything (`:311`):
   ```kotlin
   val provider = providers.firstOrNull { it.isConnected() } ?: return@withLock
   ```
   — no progress, no outcome, no state change. Every **Sync now** does the same.

## Fix
1. `SyncRepositoryImpl.disconnect` (`:255-280`): everything from `provider.disconnect()` on runs under
   `withContext(NonCancellable) { … }`. It is bounded — the provider's two waits are 10 s each
   (`REVOKE_TIMEOUT_MILLIS`) — and a half-done disconnect is worse than one that takes its ten seconds. The
   `accountRefreshJob?.cancel()` and `syncJob?.cancelAndJoin()` before it stay outside, so the caller can still be
   cancelled while waiting for the run. Replace the `catch (exception: Exception)` at `:267` by
   `catch (exception: CancellationException) { throw exception } catch (exception: Exception) { … }` anyway, so the
   swallowing cannot come back if the block is ever moved. KDoc: "Carried to its end once it has started taking the
   connection apart: the credentials go first, and a disconnect stopped after that would leave an account on screen
   that nothing can sync."
2. `runSynchronization` (`:311`): a `Connected` state with no connected provider is not a run to skip silently but a
   connection that is gone. Replace the early return with
   ```kotlin
   val provider = providers.firstOrNull { it.isConnected() } ?: run {
       // The credentials are gone while the screen still shows the account - a disconnect that did not get to the
       // end, a storage that lost them. Saying so is the only way the user gets a button that works.
       updateConnected { SyncState.ConnectionFailed(it.account.providerId, SyncFailureReason.AUTHORIZATION) }
       return@withLock
   }
   ```
   (`ConnectionFailed(AUTHORIZATION)` already reads "connect again" in `SyncSettings`.) This alone makes the dead state
   impossible to stay in, whatever produced it.
3. Do **not** move the disconnect into the repository's `scope` with a fire-and-forget launch: `launchLibraryChange`
   is what reports a failure of it to the user (`Message.OperationFailed`), and that must keep working.

## Tests
`SyncRepositoryImplTest` (give `FakeSyncProvider` a `var connected = true` returned by `isConnected()`, and a
`var onDisconnect: suspend () -> Unit = {}` run by `disconnect()` after setting `connected = false`):
- `a disconnect that is cancelled after the credentials went still ends disconnected`: `onDisconnect =
  { awaitCancellation() }` would hang under NonCancellable, so use `onDisconnect = { delay(1_000) }`; launch
  `repository.disconnect()`, wait until `provider.connected` is false, cancel the job; then
  `repository.syncState.first { it == SyncState.Disconnected }` and `stateLocalSource.index == null`.
- `a run asked for after the credentials went reports the connection as failed`: restore with a connected fake, set
  `provider.connected = false`, `synchronize(ASK)`, then
  `syncState.first { it is SyncState.ConnectionFailed }` has reason `AUTHORIZATION`.

## Verify
1. Android emulator, debug build with a Dropbox key, connected account; throttle the emulator's network (Extended
   controls → Cellular → Network type GPRS).
2. Disconnect → confirm → immediately Back. Reopen: Settings shows the connect button (or, if the process had to
   finish the disconnect's ten seconds first, shows it after them).
3. Force the dead state by other means is not needed; the second test covers it.
4. `./gradlew :data:repository:implementation:desktopTest`.

## Docs
`data/repository/implementation/CLAUDE.md`, after "Disconnecting cancels a run that is still going and waits for it":
"; once it has begun to take the connection apart it is carried to the end whoever cancels its caller, and a run that
finds the state `Connected` but no provider connected turns it into `ConnectionFailed` rather than doing nothing, so an
account whose credentials are gone never stays on screen with a button that cannot work."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing; 05 edits the catch blocks further down in `runSynchronization`, so schedule the two one after the other.
