# Keep a refused connection shown as failed when the reconnect started from it is cancelled

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`,
`data/repository/implementation/CLAUDE.md`
**Challenged:** amended — the failed state is only returned to when the provider still holds credentials (a first connection that failed stored none, and must still back out to Disconnected); added the test for that.

## Problem

A run the service refuses ends in `SyncState.ConnectionFailed(provider, AUTHORIZATION)`, and the refused tokens and
the index are kept on purpose, so that reconnecting the same account keeps the index (`SyncRepositoryImpl.kt:598-604`
at 8ee010b36). Settings then shows the "authorization refused" reason with Connect. If the user taps Connect and then
backs out — closes the browser / consent sheet (`AuthorizationOutcome.Cancelled()` with no message), or presses the
in-app cancel (`cancelConnection`, or the `CancellationException` branch) — every one of those paths sets
`Disconnected` (`:292-305`, `:307-312`, `:321-325`):

```kotlin
is SyncAuthenticator.AuthorizationOutcome.Cancelled -> {
    discardPendingAuthorization()
    _syncState.update { if (outcome.message == null) SyncState.Disconnected else SyncState.ConnectionFailed(...) }
```

```kotlin
override suspend fun cancelConnection() {
    if (_syncState.value !is SyncState.Connecting) return
    discardPendingAuthorization()
    _syncState.update { if (it is SyncState.Connecting) SyncState.Disconnected else it }
}
```

`discardPendingAuthorization` → `clearPendingAuthorization` keeps the credentials document because its refresh token
is not empty (`PendingAuthorizationStoreImpl.kt:54-56`). So the screen says "Disconnected" and drops the reason, but on
the next launch `restore` finds `isConnected()` true and a stored account, shows the account as **Connected**, and the
launch run fails again with `ConnectionFailed(AUTHORIZATION)`. The user was told one thing and the app does another.

## Fix

Go back to the state the connect started from when that was a failure, instead of always to `Disconnected`:

- Add `private var stateBeforeConnecting: SyncState? = null` to `SyncRepositoryImpl`.
- In `connect`, before `_syncState.update { SyncState.Connecting(providerId) }`, record the current state when it is a
  failure **and** the provider still holds credentials — the only case the next launch would restore as connected. A
  `ConnectionFailed` reached by a first connection that never stored any (a refused write, a page that answered with an
  error message, a token exchange that failed) is not one of them: its next launch is `Disconnected`, so backing out of
  a retry must say `Disconnected` too, not repeat the old failure.

  ```kotlin
  stateBeforeConnecting = _syncState.value.takeIf { it is SyncState.ConnectionFailed && hasStoredCredentials(provider) }
  ```

  with

  ```kotlin
  /** Whether [provider] still holds credentials, which is what the next launch restores a connection from. */
  private suspend fun hasStoredCredentials(provider: SyncProvider) = try {
      provider.isConnected()
  } catch (exception: CancellationException) {
      throw exception
  } catch (exception: Exception) {
      false
  }
  ```

  Asked before `savePendingAuthorization` writes anything, so the answer is about the credentials the failure left.
- Add a helper used by the three backing-out paths:

  ```kotlin
  /**
   * What backing out of an authorization returns to: a connection that had failed stays failed, since its
   * credentials are still stored and the next launch would otherwise restore it as connected; anything else is
   * disconnected.
   */
  private fun stateAfterBackingOut() = stateBeforeConnecting ?: SyncState.Disconnected
  ```

  and use it in the `Cancelled` branch with no message, in the `CancellationException` branch of `connect`, and in
  `cancelConnection` (`if (it is SyncState.Connecting) stateAfterBackingOut() else it`).
- Clear `stateBeforeConnecting` (set it to null) when an authorization completes (`completePendingAuthorization`
  success), on `disconnect`, and after it has been used.

Ordering: `CampfireViewModel.cancelSyncConnection` cancels and joins the connect job *before* calling
`cancelConnection`, so it is the `CancellationException` branch that uses (and clears) the saved state, and
`cancelConnection` then finds no `Connecting` state and returns; `cancelConnection` only uses it on its own for a
`Redirected` attempt still in this process. On the web a redirect ends the page, so a new one starts with nothing
saved and backs out to `Disconnected`, as today — its next `restore` reports the refusal again anyway.

The paths that already end in a `ConnectionFailed` (a `Cancelled` with a message, an exception) are unchanged. Add a
sentence to the `data/repository/implementation/CLAUDE.md` passage about `ConnectionFailed(AUTHORIZATION)` keeping
the tokens: backing out of the reconnect returns to that failed state rather than to disconnected.

## Tests

In `SyncRepositoryImplTest`, next to `a run the service refuses reports the connection as failed and keeps the index`:
`backing out of reconnecting a refused connection keeps it failed` — reach `ConnectionFailed(AUTHORIZATION)` the same
way, then `connect(...)` with `FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled())`
→ the state is `ConnectionFailed` with reason `AUTHORIZATION`. And `cancelling the reconnect of a refused connection
keeps it failed` — an authenticator whose `onAuthorize` suspends (as the existing tests that keep the user on the
consent page do), `cancelConnection()` while it waits → `ConnectionFailed(AUTHORIZATION)`. Keep (or add) the case that
a first connect that is cancelled ends `Disconnected`. Add `backing out of retrying a connection that never stored
credentials ends disconnected`: a first `connect` with a `FakePendingAuthorizationStore` whose `onWrite` throws ends in
`ConnectionFailed(STORAGE)` (as `a connection whose storage refuses every write ends as a failure` does); then set
`provider.connected = false` (no credentials — `FakeSyncProvider.connected` defaults to true), let writes succeed
again, and `connect` with `Cancelled()` → `SyncState.Disconnected`.

Run `./gradlew :data:repository:implementation:desktopTest`.

## Manual check

With Dropbox connected, revoke Campfire's access in the Dropbox account settings (Connected apps), run Sync now:
Settings says the authorization was refused. Tap Connect and close the consent page without approving. Settings still
says the authorization was refused (not "Disconnected"), with Connect and Disconnect still offered. (A restart then
behaves as it does for any refused connection the user has not touched: the account is restored and the launch run
reports the refusal again — what the screen said before the restart is now consistent with that.)
