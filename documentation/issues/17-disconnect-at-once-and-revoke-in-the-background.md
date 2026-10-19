<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Disconnect at once on a slow network, revoking the token in the background afterwards

**Challenged:** amended — the manager keeps the background revocations' jobs and cancels any still going when a new
authorization starts (`connect`, and plan 16's restore exchange), so a revoke of the old grant that lands late can never
meet a connection made right after it; the provider captures and forgets the credentials in one
`SyncCredentialsStore.update` rather than a `load` and a `save`; `data/source/remote/implementation/CLAUDE.md` (its
`disconnect` bullet) is added to the files; the "Manual check" section is removed.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** U (touches lane T's `DropboxSyncProvider.kt`: run this plan only after lane T has merged, and build on its
version of the file)
**Files:** `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncConnection.kt`,
`data/source/remote/api/CLAUDE.md`,
`data/source/remote/implementation/CLAUDE.md`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt` (lane T's file),
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokensTest.kt` or a new `DropboxDisconnectTest.kt` beside it,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManager.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManagerTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/SyncController.kt`

## Problem

`DropboxSyncProvider.disconnect` renews the token, then forgets it, then revokes it, and each network step has a 10 s
bound:

```kotlin
val accessToken = try {
    withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { tokens.accessToken() }
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    null
}
credentialsStore.save(null)
if (accessToken.isNullOrEmpty()) return
// Best effort: the local credentials are already gone, and a token that cannot be revoked simply expires.
try {
    withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) {
        transport { httpClientHolder.client().post(REVOKE_URL) { header("Authorization", "Bearer $accessToken") } }
    }
} …
```

`SyncConnectionManager.disconnect` waits for all of it before the state changes:

```kotlin
) { if (provider.isConnected()) provider.disconnect() }
}
runner.withRunLock {
    indexStore.clear()
    stateBeforeConnecting = null
    stateHolder.update { SyncState.Disconnected }
}
```

Offline or on a weak signal, after the user confirms Disconnect the dialog closes, and for 10–20 s Settings still says
"Connected as X", with Sync now and Disconnect enabled and nothing showing that anything is happening. The user taps
Disconnect again. `SyncController.disconnectSyncProvider` has no guard
(`messageSink.launchLibraryChange { disconnectSyncProvider.invoke() }`), so a second confirmation starts a second
disconnect beside the first.

## Fix

Options:

- **(a) Recommended: forget locally first and switch to Disconnected, then revoke in the app's scope.** The user's
  part of a disconnect is local (the credentials and the index), and only the courtesy to Dropbox needs the network.
- (b) A new `SyncState.Disconnecting` with the rows disabled. That means a new state across `:data:model`, the
  presentation's `stage`, `SyncSettings`, the onboarding's `SyncStep`, and strings, and the user still waits 10–20 s
  for nothing they need. Not recommended.

For (a), the order matters. The revoke needs an access token, and a stored one has usually expired (Dropbox's last
four hours), so it needs the refresh token too. Both must be captured before the credentials are forgotten, and
**never written back**: a renewal that ran after the forget and stored its result would bring the connection back, or
overwrite the credentials of an account connected in the meantime.

1. **Contract** (`SyncConnection`, `:data:source:remote:api`): change `suspend fun disconnect()` to
   `suspend fun disconnect(): (suspend () -> Unit)?`, with this KDoc: forgets the stored credentials at once, without a
   request, and answers the request that tells the service to drop them, for the caller to run in the background. It
   may take as long as the network does, never throws but cancellation, and stores nothing. Null means there is nothing
   to revoke. Update `data/source/remote/api/CLAUDE.md`'s "`disconnect` revokes the token" line.
   (A small `fun interface SyncRevocation { suspend fun revoke() }` reads better at call sites, and is equally
   acceptable.)
2. **`DropboxSyncProvider.disconnect`**: capture and forget in one step,
   `val credentials = credentialsStore.update { current -> null to current?.takeIf { it.providerId == id.id } }`, so no
   renewal can land between the read and the forget; then return null if there were none. Otherwise return a block that:
   - uses `credentials.accessToken` if it is non-empty and not near `expiresAt` (the check `DropboxTokens.accessToken`
     makes), else renews with `tokens.exchange("grant_type=refresh_token&refresh_token=…&client_id=…")` from the
     **captured** refresh token. `exchange` stores nothing. Do not call `tokens.accessToken()`, which reads and writes
     the store.
   - posts the revoke through `transport`, under the existing `REVOKE_TIMEOUT_MILLIS` bounds (one per request), and logs
     and swallows every failure but cancellation, as today.

   Building the form here duplicates one line of `DropboxTokens.accessToken`. Prefer a small `DropboxTokens.renew(
   refreshToken: String): DropboxTokenResponse` that both use, if lane T has left `DropboxTokens` open to it, and say so
   in the commit.
3. **`SyncConnectionManager.disconnect`**: keep `accountRefreshJob?.cancel()` and `scheduler.stopForDisconnect()`.
   Inside `NonCancellable`, collect the revocations
   (`providers.mapNotNull { provider -> recovering(…) { if (provider.isConnected()) provider.disconnect() else null } }`),
   then `runner.withRunLock { indexStore.clear(); stateBeforeConnecting = null; stateHolder.update { Disconnected } }`
   as now, and only then `revocations.forEach { revocationJobs += scope.launch { it() } }` in the manager's application
   scope. `revocationJobs` is a private list guarded like the manager's other mutable fields (an `AtomicReference` of a
   list, or under a small `Mutex`), its finished jobs dropped as new ones are added. `connect()` (before it writes the
   pending authorization) and the restore exchange of plan 16 (before `completePendingAuthorization`) cancel every
   revocation still going: a late revoke of the old grant must not meet a connection made seconds later, whatever
   Dropbox's revoke reaches (its docs say the token's refresh token and its access tokens, but a new grant of the same
   account and app is exactly what nobody should have to find out about). A revoke cut off that way leaves the old grant
   unrevoked, which is today's "simply expires" case. A run
   belongs to the app, and so does this request: leaving Settings doesn't stop it. On the web, a page closed within
   those seconds leaves the grant unrevoked. Its credentials are gone from the device either way, which is today's
   "simply expires" case.
4. **Double tap**: `SyncController` keeps `private var disconnectJob: Job?`, and `disconnectSyncProvider()` returns at
   once while it is active (the state-based guard `connectSyncProvider` uses; never a time debounce). The manager is
   safe even without it: a second call finds `isConnected()` false, revokes nothing, and clears an index that is already
   gone. The second confirmation is mostly prevented anyway, because the state now changes as soon as the local writes
   finish and the Connected section, its Disconnect row included, crossfades away.
5. `data/source/remote/implementation/CLAUDE.md`, the bullet "`disconnect` renews the token if needed, forgets the local
   credentials, then revokes best effort; renewal and revocation each wait at most 10 s, so a dead network never keeps
   the user connected": "`disconnect` forgets the local credentials at once and answers the revocation for the caller to
   run in the background: a renewal from the captured refresh token if needed (never stored) and the revoke, each
   bounded at 10 s." `connection/CLAUDE.md`, the Disconnecting bullet: "…then deletes the index under the run lock and shows
   Disconnected; the service is told afterwards, in the background, with the token captured before the credentials
   were forgotten."
6. `FakeSyncProvider.disconnect` returns a block running `onDisconnect`, after `connected = false`. Update the KDoc's
   note on `onDisconnect`, and check `a disconnect that is cancelled after the credentials went still ends
   disconnected`, which then tests the background block.

## Tests

- `SyncConnectionManagerTest`: `a disconnect shows disconnected before the service has answered`. The provider's
  revocation suspends on a `CompletableDeferred` that is never completed. Call `repository.disconnect()` (it returns),
  then `runCurrent()`: the state is `Disconnected`, the index is cleared, `provider.connected` is false, and the
  revocation was started. A follow-up `connect` can still store credentials, and the hanging revocation never touches
  them (the fake's block writes nothing).
- `a second disconnect while the first is going does nothing more`: two `disconnect()` calls, with one revocation
  started.
- `connecting again cancels a revocation still going`: the revocation suspends; a `connect` → the revocation's block
  was cancelled, and the new credentials are stored.
- Dropbox (`DropboxTokensTest` style, with `DropboxTestService`):
  - `disconnecting forgets the credentials before any request`: the store is empty when `disconnect()` returns and no
    request has been made.
  - `the revocation renews an expired token without storing it`: captured credentials with an expired access token →
    the token request and then the revoke with the new token, and the store stays empty.
  - `a revocation that cannot reach the service ends quietly`: the transport throws a network failure, and the block
    returns.
