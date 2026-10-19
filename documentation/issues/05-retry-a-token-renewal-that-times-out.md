<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Retry a token renewal that could not reach Dropbox, and bound how long one may hold the credentials lock

**Challenged:** amended — the bounded-time test as written waits 7 × 30 s of real time (`HttpTimeout`'s bound runs on the engine's dispatcher, not the test's; probed) and would fail at `runTest`'s one-minute limit; it now runs the mock engine on the test dispatcher (plan 03's `DropboxTestService` helper) and a direct check of the token request's 30 s capability is added. Fix unchanged; verified `HttpRequestTimeoutException` is a `kotlinx.io.IOException` in 3.6.0.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokens.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokensTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

Drop this plan if plan 01's change already moved the token call inside the retried `try` and made a renewal's
`SyncNetworkException` retryable; then only the "bound the exchange" half below is left, and it may be folded into
whatever remains. Assumes 01 has landed (non-timeout transport failures retried, `MAXIMUM_TRANSPORT_FAILURE_RETRIES`).

## Problem

In `DropboxTransport.request` (`dropbox/DropboxTransport.kt:90-101`) the token is asked for inside `transport { }` but
outside the inner `try` that decides on a retry:

```kotlin
val response = transport {
    token = tokens.accessToken(refused = refusedToken)
    try {
        block(token)
    } catch (exception: Exception) {
        …
        if (attempt >= MAXIMUM_RETRIES || !exception.isTimeout()) throw exception
        null
    }
}
```

A renewal is due every four hours of use and on the first call after an idle night, so it is the first request of most
runs. If it times out (or after 01, fails any other way), `DropboxTokens.exchange` wraps it in its own `transport { }`,
which throws `SyncNetworkException` straight out of `request`: the run ends on the first attempt, the one place in the
transport where a timeout is not retried.

The exchange also runs inside `SyncCredentialsStore.update { }` (`DropboxTokens.accessToken`), whose `Mutex` every
`load()` takes too, so while a renewal is stalled `isConnected()`, `storedAccount()`, and `disconnect()` wait for it: up
to 60 s of silence per attempt natively, 10 minutes on the browser. In the app this is mitigated (checked at HEAD):
`SyncConnectionManager.disconnect` cancels `accountRefreshJob` and calls `scheduler.stopForDisconnect()`
(`syncJob.exchange(null)?.cancelAndJoin()`) before `provider.isConnected()`, and cancelling the renewal's coroutine
releases the lock; `DropboxSyncProvider.disconnect` bounds its own renewal with `withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS)`.
What is left is a renewal nobody cancels holding the lock for its whole stall, and the retry above.

## Fix

1. Move the token call into the retried section. Inside the inner `try`, call `tokens.accessToken(refused = refusedToken)`
   before `block(token)`, and in the catch treat a `SyncNetworkException` thrown by the renewal as a transport failure to
   retry (its `cause` is the transport's throwable; `exchange` throws it bare for 429/5xx, which is also worth another
   attempt). A `SyncAuthorizationException` (not connected, or refused) must be rethrown at once, so check it first:

   ```kotlin
   } catch (exception: SyncAuthorizationException) {
       throw exception
   } catch (throwable: Throwable) {
       currentCoroutineContext().ensureActive()
       val cause = (throwable as? SyncNetworkException)?.cause ?: throwable
       val limit = if (cause.isTimeout()) MAXIMUM_RETRIES else MAXIMUM_TRANSPORT_FAILURE_RETRIES
       if (attempt >= limit) throw throwable
       null
   }
   ```

   The back-off `delay` stays outside `transport` and outside `SyncCredentialsStore.update`, so the lock is never held
   across a wait. Keep the comment above the token call (a renewal whose answer cannot be decoded stays a network
   failure of the run); after plan 07 that also holds for the code-exchange path.
2. Bound the exchange as a whole: in `DropboxTokens.exchange`, add `timeout { requestTimeoutMillis = TOKEN_TIMEOUT_MILLIS }`
   to the `post`. **Recommended: 30 s.** The answer is a few hundred bytes on every engine, so a total bound shortens
   nothing that could finish, and it is safe natively too. It bounds how long a renewal holds the credentials lock, and
   the code exchange of the authorization flows gets the same bound. A timeout there is an `HttpRequestTimeoutException`
   (an `IOException` in Ktor 3.6.0, not a cancellation), which `transport` makes a `SyncNetworkException`.

A resent refresh is safe: Dropbox does not rotate refresh tokens on use (the code keeps a new one if it is ever handed
out), so asking twice with the same one gets two access tokens.

Update `data/source/remote/implementation/CLAUDE.md`, OAuth section: "Tokens renew inside one
`SyncCredentialsStore.update` …" gains "bounded at 30 s, and retried like any call when it cannot reach Dropbox".

## Tests

In `DropboxTokensTest` (expired stored token, so `list()` renews first; virtual time):

- `a renewal that times out is tried again`: the token endpoint throws `SocketTimeoutException` on its first call and
  then renews; `list()` succeeds.
- `the token request carries its own bound`: the token endpoint records
  `request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis`; it is 30,000, for the renewal and for
  `completeAuthorization`'s exchange alike.
- `a renewal that never answers ends the run in bounded time`: the client installs `HttpTimeout` (as
  `DropboxRequestTest` does) and the mock engine runs on `StandardTestDispatcher(testScheduler)` (the optional engine
  dispatcher plan 03 adds to `dropboxProvider`; without it the 30 s bound passes in real time), the token endpoint
  `awaitCancellation()`s; `assertFailsWith<SyncNetworkException> { provider.list() }` with `currentTime` below
  7 × 30 s + 94 s + jitter (about 5.2 minutes), and the token endpoint was asked 7 times.
- `a refused renewal is not tried again`: 400 `invalid_grant` → `SyncAuthorizationException`, and the token endpoint was
  asked once (the count is the behaviour: a refusal must not be retried).
