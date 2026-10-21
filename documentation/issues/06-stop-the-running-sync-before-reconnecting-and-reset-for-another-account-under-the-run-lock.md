# Stop the running sync before reconnecting a connected account, reset for another account under the run lock, and keep working credentials when a reconnect's token exchange fails

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — the order in `connect` is fixed (remember, then stop, then `Connecting`); the new back-out on a
failed exchange schedules a run too, since the scheduler was stopped; a test for it; the journal applier's residual
window named.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManager.kt`,
`…/connection/ConnectionBeforeConnecting.kt`, `…/connection/CLAUDE.md`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunScheduler.kt`
(rename of `stopForDisconnect` only, if taken), `…/scheduling/CLAUDE.md`, `data/sync/implementation/CLAUDE.md` (Locks),
`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncConnection.kt`
(KDoc of `completeAuthorization`),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManagerTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/messages/Message.kt`, `…/ui/messages/Messages.kt`,
`…/ui/sync/SyncController.kt` (and its test, if any), `presentation/src/commonMain/composeResources/values/strings.xml`,
`…/values-hu/strings.xml`

(`…/connection/` = `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/`,
`…/scheduling/` likewise.) Line numbers are at the reviewed commit `a869c9ff7`; find the code by its snippet.

Lane K, alone: shares no file with lane R (plans 01–04) but `FakeSyncProvider.kt`, where plan 04 changes only `downloadDocument`. `forgetSharedLinks` is called as it is today.

## Problem

Settings → Sharing and the export screen offer **Connect again** to a connected account whose token predates the
permission to make links (`SyncScopeMissingException`), so `SyncConnectionManager.connect` (`:227`) now runs from
`Connected`. It was written for the disconnected and failed states:

**1. A running run carries on through the reconnect.** `connect` sets `Connecting` and authorizes; it never stops the
scheduler, while `disconnect` (`:298`) does (`scheduler.stopForDisconnect()`). On a phone, going to the consent page
also takes the app out of the front, which may start a scheduled run (`startScheduledSynchronization`). The run holds
the account it started with (`SyncRunner.run`: `val connected = stateHolder.value as? SyncState.Connected`, and
`accountId = connected.account.indexKey()`), and its requests use whatever tokens are stored at the moment.

**2. Choosing another account at the consent page swaps everything under that run.** In
`completePendingAuthorization` (`:396-449`), `provider.completeAuthorization(...)` stores the new account's tokens,
then, outside any lock:

```kotlin
val document = indexStore.loadOrNull()?.adoptedBy(account)
if (document != null && document.accountId != account.indexKey()) {
    indexStore.save(SyncIndexDocument())
    forgetSharedLinks()
}
```

The run started for account A now lists, uploads, and deletes in account B's folder against A's index (B's files read
against A's index as deletions and new files, the deletion guard being the only brake), and ends by saving A's index,
filed under A's key, over the reset; `SharedCopySync` may write the mirror right after `forgetSharedLinks` cleared it.
Its outcome then lands in B's `Connected` state ("a run writes its outcome only into a state still Connected").
`disconnect` does the same reset under `runner.withRunLock` precisely to avoid this (`:309-315`).

**3. A reconnect whose token exchange fails forgets the working connection.** `completePendingAuthorization`'s
`catch (exception: Exception)` calls `forgetCredentialsOf(provider)`. In `DropboxSyncProvider.completeAuthorization`,
the exchange (`tokens.exchange`) is the only call that can fail before anything is stored (the store's own write aside;
`loadAccount` catches everything), so a network blip at that moment deletes the account's old, working tokens: the
user who tapped Connect again to make links ends with a failed connection and must connect from scratch.
`ConnectionBeforeConnecting`'s KDoc already describes this ("a token exchange given up halfway forgets whatever the
provider holds, the old tokens with it") as if it were intended.

## Fix

1. **Stop the scheduler when connecting from a stored connection.** In `connect`, after
   `connectionBefore.remember(...)` (so that the state remembered is the one the user saw, not the stopped run's
   `Interrupted`, which would also keep `synchronizeOnReturn` from starting runs) and before
   `stateHolder.update { SyncState.Connecting(providerId) }`, when the state is `Connected` (or `ConnectionFailed`,
   for symmetry; no run goes then, but a retry may wait): `accountRefresh.cancel()` and `scheduler.stopForDisconnect()`
   (rename it `stopForConnectionChange` and update its KDoc and callers, since it now serves both). It cancels and
   joins the run, drops a waiting run or retry, and forgets the watch point, so the feed starts nothing meanwhile.
   - When the user backs out to the remembered `Connected` state (Cancel, closed page, the cancellation path, and
     `cancelConnection`, and fix 3's failed exchange), call `scheduler.schedule()` after the state is restored, so the stopped run's work goes on
     after `AUTOMATIC_RUN_DELAY`. A successful connect already starts a run (`ConnectSyncProviderUseCaseImpl`).
   - A run that `restore` or the debounce would start while `Connecting` is shown returns at once
     (`SyncRunner.run` requires `Connected`), so nothing else is needed for the time the consent page is open.
2. **Reset for another account under the run lock.** Wrap the index check, the `indexStore.save(SyncIndexDocument())`,
   and `forgetSharedLinks()` in `runner.withRunLock { … }`, and do the `stateHolder.update { SyncState.Connected(...) }`
   inside it too, so that no run can start between the reset and the new state: as `disconnect` does. Residual, not
   fixed here: the journal applier and sharing actions never take the run lock, so an application started for the old
   account a moment before the new tokens are stored could write into the new account's list; its window is seconds
   after a rename or deletion, against a consent page's round trip. Update
   `data/sync/implementation/CLAUDE.md`'s Locks bullet ("held by a run, and by `disconnect` around deleting the index")
   to name this second holder.
3. **Keep the working tokens when the exchange fails.**
   **Decided (2026-10-21):** included here, with a message telling the user the reconnect did not go through (fix 3a).
   - Contract, in `SyncConnection.completeAuthorization`'s KDoc: it stores nothing until the code has been exchanged; a
     `SyncNetworkException` or `SyncAuthorizationException` it throws comes from the exchange, before anything is
     stored. Dropbox already behaves so; `FakeSyncProvider.onCompleteAuthorization` needs nothing new.
   - In `completePendingAuthorization`'s `catch (exception: Exception)`: when the exception is one of those two and
     `connectionBefore` remembered a stored connection, do not call `forgetCredentialsOf(provider)`; log, put back
     `connectionBefore.stateAfterBackingOut()` (which checks the credentials are still there), `connectionBefore.forget()`,
     and answer `false`. Otherwise (a first connection, the store failing, cancellation) keep today's forgetting.
     `ConnectionBeforeConnecting` needs a way to say whether it remembered something (`val hasStoredConnection`).
   - Out of reach: a reconnect finished at a later start up (web, Android after process death) has no
     `connectionBefore`, and keeps today's behaviour. Say so in `connection/CLAUDE.md`.
   - Fix `ConnectionBeforeConnecting`'s KDoc sentence quoted above, and `connection/CLAUDE.md`'s "every exit not ending
     connected forgets the stored credentials" (true for the credentials the authorization stored, not for the ones it
     started from).
3a. **Tell the user.** A reconnect that backs out because the exchange failed must not look like a Cancel. Add
   `Message.ReconnectFailed` (`presentation/.../ui/messages/Message.kt`, KDoc: "a reconnect of a working connection
   failed before the new permission was stored; the connection the user had still works") and show it in `Messages.kt`
   with new strings `settings_sync_reconnect_failed`: English "Couldn't connect Dropbox again. The connection you had
   still works." and Hungarian "Nem sikerült újra csatlakoztatni a Dropboxot. A meglévő kapcsolat továbbra is működik."
   (put both in a commented group of their own in `values/strings.xml` and `values-hu/strings.xml`). The seam: a
   `ConnectSyncProviderUseCase` answers only a Boolean, which is false for a Cancel too. Find the smallest honest way
   to tell them apart before choosing: recommended, `SyncController.connectSyncProvider`
   (`presentation/.../ui/sync/SyncController.kt`) notes whether the state was `Connected` when it started and whether
   `cancelSyncConnection` was called meanwhile; when the use case answers false, the state is `Connected` again, and
   the user did not cancel, it sends `Message.ReconnectFailed` through `messageSink`. If that proves unreliable (for
   example, a closed consent page also lands there; that is acceptable, since it too means the reconnect did not go
   through), a one-off event from `SyncConnectionManager` is the alternative; do not change the use case's return type
   unless nothing smaller works.
4. `connection/CLAUDE.md`: add that connecting from a stored connection stops the running run first and resumes it on
   backing out; `scheduling/CLAUDE.md`: the renamed stop.

## Tests

`./gradlew :data:sync:implementation:desktopTest`

`SyncConnectionManagerTest` (it already has the reconnect cases near `cancelling the reconnect of a working connection
keeps it connected`):

- `reconnecting a working connection stops the run that is going`: start a run that suspends (a `FakeSyncProvider`
  `onList` awaiting a `CompletableDeferred`), call `connect` with an authenticator that waits; the run's job is
  cancelled before `authorize` is asked.
- `backing out of reconnecting a working connection schedules a run`: after Cancel, advancing by
  `AUTOMATIC_RUN_DELAY` starts one (`provider.listCount` grows).
- `another account at the consent page resets the index under the run lock`: hold the run lock from the test
  (`runner.withRunLock` with a gate) while the authorization completes with another account; the index is not reset
  and the state not `Connected` until the gate opens.
- `a reconnect whose token exchange cannot reach the service keeps the working connection`:
  `onCompleteAuthorization = { throw SyncNetworkException("offline") }` from `Connected`; the state is that `Connected`
  again, `provider.hasForgottenCredentials` is false, and advancing by `AUTOMATIC_RUN_DELAY` starts a run. The same from `Disconnected` still forgets (existing behaviour).

If a `SyncController` test exists, add: a false answer from `Connected` without a cancel sends `ReconnectFailed`, and a
cancel sends nothing (otherwise a pure helper deciding it is tested instead).

Use `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle` (`scheduling/CLAUDE.md`).
