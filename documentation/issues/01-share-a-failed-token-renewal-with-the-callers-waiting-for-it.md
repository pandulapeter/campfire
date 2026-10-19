<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Share a token renewal that failed for the network with every caller that waited for it, instead of each starting its own

**Challenged:** sound

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T (first; plan 02 follows in the same worktree)
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokens.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokensTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

Since 33aab2aee, `DropboxTransport.request` (`dropbox/DropboxTransport.kt`) asks for the token inside its retried
`transport { try { … } }`, so a renewal that cannot reach Dropbox is retried like any call (a timeout up to
`MAXIMUM_RETRIES` = 6 times, with the 2/4/8/16/32/32 s back-off between):

```kotlin
val response = transport {
    try {
        token = tokens.accessToken(refused = refusedToken)
        block(token)
    } catch (exception: SyncAuthorizationException) {
        throw exception
    } catch (throwable: Throwable) {
        currentCoroutineContext().ensureActive()
        val cause = (throwable as? SyncNetworkException)?.cause ?: throwable
        val limit = if (cause.isTimeout()) MAXIMUM_RETRIES else MAXIMUM_TRANSPORT_FAILURE_RETRIES
        if (attempt >= limit) throw throwable
        null
    }
}
```

`DropboxTokens.accessToken` (`dropbox/DropboxTokens.kt`) runs the whole check and the renewal inside the credentials
lock, and the renewal is bounded at `TOKEN_TIMEOUT_MILLIS` (30 s):

```kotlin
suspend fun accessToken(refused: String? = null): String = credentialsStore.update { credentials ->
    ...
    if (credentials.accessToken != refused && hasUsableAccessToken(credentials)) {
        return@update credentials to credentials.accessToken
    }
    val token = renew(credentials.refreshToken)
    ...
}
```

The lock was meant to make concurrent callers share one renewal, and it does when the renewal succeeds (the waiters
find a usable token). When it fails for the network, it does the opposite: every caller waiting for the lock enters it
in turn, finds the token still expired, and starts a 30 s renewal of its own against the same stalled endpoint, and
each caller's next attempt queues behind all the others'. With a token endpoint that accepts but never answers and an
expired token, measured at HEAD with `MockEngine` and virtual time (the setup of the existing test
`a renewal that never answers ends the run in bounded time`, with N concurrent `provider.list()` calls):

| concurrent callers | time until all fail | token exchanges |
|---|---|---|
| 1 | 305 s | 7 |
| 2 (the launch's `SyncAccountRefresh` and the run's `list()`) | 424 s | 14 |
| 6 (in-flight transfers after a mid-run 401) | 1260 s (21 min) | 42 |

The run sits on "syncing" for up to 21 minutes before it ends as NETWORK, and the credentials lock (which
`loadAccount`'s store update and disconnect also wait for) is held for nearly all of it.

## Fix

Options:

- **A. Remember the last renewal that failed for the network, and let a caller that waited through it fail with it
  (recommended).** Each caller notes the last failure before it waits for the lock; inside the lock, a *newer* failure
  for the same refresh token means a renewal failed while it was waiting, and it fails the same way without an exchange.
  Clock-free, needs no scope of its own, changes nothing outside `accessToken`. N callers cost one renewal per retry
  round: they all back off for about the same time (the same attempt count, under 0.5 s jitter), the first one back
  renews, and the rest arrive within its 30 s and share the outcome.
- B. Single-flight renewal: one shared `Deferred` per refresh token, awaited outside the lock. It needs a
  `CoroutineScope` owned by `DropboxTokens` so that one caller's cancellation does not cancel the others' renewal,
  which then outlives a stopped run, and the test's virtual time has to reach that scope. More moving parts for the
  same result.
- C. A time window ("a renewal failed less than N s ago"): needs an injectable clock to be testable under virtual
  time (a real clock reads ~0 elapsed in a virtual-time test, so every retry would be refused without an exchange and
  the existing 7-exchange test would break), and the window is a guess.

Take **A**. In `DropboxTokens`:

```kotlin
/** A renewal that could not reach Dropbox, kept so that the callers who waited for it fail with it, see [accessToken]. */
private class FailedRenewal(val refreshToken: String, val failure: SyncNetworkException)

private val lastFailedRenewal = AtomicReference<FailedRenewal?>(null)

suspend fun accessToken(refused: String? = null): String {
    // Read before waiting for the lock: a failure newer than this one happened while this caller waited.
    val seen = lastFailedRenewal.load()
    return credentialsStore.update { credentials ->
        if (credentials == null || credentials.providerId != providerId.id) {
            throw SyncAuthorizationException("Dropbox is not connected.")
        }
        if (credentials.accessToken != refused && hasUsableAccessToken(credentials)) {
            return@update credentials to credentials.accessToken
        }
        val failed = lastFailedRenewal.load()
        if (failed != null && failed !== seen && failed.refreshToken == credentials.refreshToken) {
            // A new instance, with the same cause, so DropboxTransport.request gives it the same retry limit.
            throw SyncNetworkException(failed.failure.message ?: "Dropbox could not be asked for a token.", failed.failure.cause)
        }
        val token = try {
            renew(credentials.refreshToken)
        } catch (exception: SyncNetworkException) {
            lastFailedRenewal.store(FailedRenewal(credentials.refreshToken, exception))
            throw exception
        }
        lastFailedRenewal.store(null)
        credentials.copy(...) to token.accessToken   // unchanged
    }
}
```

- `AtomicReference` is `kotlin.concurrent.atomics` (`@file:OptIn(ExperimentalAtomicApi::class)`, as
  `data/sync/implementation/.../scheduling/SyncRunScheduler.kt` does); the file already opts in to `ExperimentalTime`,
  so list both. It is written only under the lock; the atomic is for the read before it. Identity (`!==`) is the
  comparison, so no counter is needed.
- Only `SyncNetworkException` is remembered. A `SyncAuthorizationException` (400/401) is not: each waiting caller
  makes its own exchange, which is answered at once, and every caller still gets the refusal (unchanged behaviour,
  and the settled "only 400/401 disconnects" rule is untouched). A cancellation is not remembered either: a caller
  cancelled mid-renewal throws `CancellationException` (the `transport` in `exchange` rethrows it only while that
  coroutine really is cancelled), so the callers waiting behind it renew for themselves.
- `renew` and `exchange` are not changed, so disconnect's revocation (`DropboxSyncProvider.revoke`, which calls
  `tokens.renew` on the captured refresh token, d9fce8818) and the authorization code exchange neither read nor write
  the memory.
- Rewrite the KDoc above `accessToken` to say both halves: a successful renewal is shared because the waiters find its
  token stored; a failed one because the waiters fail with it, so the callers of a run cost one renewal per retry
  rather than one each, and a stalled token endpoint ends the run in the time one caller would take.
- `data/source/remote/implementation/CLAUDE.md`, section "OAuth", first bullet ("Tokens renew inside one
  `SyncCredentialsStore.update`, so concurrent requests share one refresh, bounded at 30 s, and retried like any call
  when it cannot reach Dropbox."): add that a renewal that could not reach Dropbox is shared too, by the callers that
  waited for it failing with it.

No change to `DropboxTransport`: the waiters' rethrow goes through the same `catch (throwable: Throwable)` and counts
as one of their attempts (plan 02 then moves it to the counter of failures without an answer).

## Tests

In `DropboxTokensTest.kt` (`./gradlew :data:source:remote:implementation:desktopTest`), with the existing harness
(`dropboxProvider`, `StoredSyncCredentials(connectedCredentials(expiresAt = 0))`, `TOKEN_URL`, `EMPTY_LISTING`):

1. **`callers waiting for a renewal that never answers fail with it rather than each trying their own`** — the setup of
   `a renewal that never answers ends the run in bounded time` (`configure = { install(HttpTimeout) }`,
   `engineDispatcher = StandardTestDispatcher(testScheduler)`, the token handler counting and then
   `awaitCancellation()`), but six callers: `coroutineScope { repeat(6) { launch { assertFailsWith<SyncNetworkException> { provider.list() } } } }`.
   Assert `currentTime < 7 * 30_000L + 94_000L + 7 * 500L` (one caller's bound; HEAD takes 1260 s) and
   `tokenRequestCount == 7` (the count of exchanges is the behaviour here: one per round, not one per caller; HEAD
   makes 42). The engine dispatcher must be the test's, since `HttpTimeout`'s bound runs there.
2. **`a caller waiting for a renewal that was cancelled renews for itself`** — token handler: the first request
   `awaitCancellation()`, later ones answer `{"access_token":"renewed","expires_in":14400}`; the listing answers
   `EMPTY_LISTING`. `val first = launch { provider.list() }`, `runCurrent()` until the first token request has arrived,
   `val second = async { provider.list() }`, `runCurrent()`, `first.cancel()`; assert `second.await().files` is empty
   (it succeeded) and `tokenRequestCount == 2`.
3. **`every caller waiting for a refused renewal is told it is refused`** — token endpoint answers 400
   `{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}`; two concurrent `list()` both
   `assertFailsWith<SyncAuthorizationException>`.

The existing tests `several requests refused at once renew the token once`, `a renewal that times out is tried again`,
`a renewal that never answers ends the run in bounded time` (still 7 exchanges for one caller), `a refused renewal is
not tried again`, and `DropboxDisconnectTest` must pass unchanged.
