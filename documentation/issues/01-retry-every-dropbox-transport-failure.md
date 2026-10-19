<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Retry a Dropbox call after any transport failure, not only after a timeout

**Challenged:** amended — the resend-safety argument for uploads was wrong (Dropbox answers a resend of identical bytes with the file's metadata, not `path/conflict`); corrected in the analysis and the KDoc wording. The fix itself is unchanged.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxTransport.request` (`dropbox/DropboxTransport.kt:90-101`) retries a failed call only when the failure is a
timeout:

```kotlin
val response = transport {
    token = tokens.accessToken(refused = refusedToken)
    try {
        block(token)
    } catch (exception: Exception) {
        // Null asks for another attempt; the last one is left to transport, which makes it the run's
        // network failure as before.
        currentCoroutineContext().ensureActive()
        if (attempt >= MAXIMUM_RETRIES || !exception.isTimeout()) throw exception
        null
    }
}
```

`isTimeout()` is `HttpRequestTimeoutException || ConnectTimeoutException || SocketTimeoutException`. Every other
transport failure ends the whole run on its first occurrence: a connection reset (`ECONNRESET`), OkHttp's "unexpected end
of stream", iOS `NSURLErrorNetworkConnectionLost` (-1005, surfaced as `DarwinHttpRequestException`), CIO's
`IllegalStateException` "Content-Length mismatch", or a DNS failure during a cell handover. Probe: one attempt, then
`SyncNetworkException`. On a phone moving between cells, a first sync of a few hundred songs often meets one of these;
the run ends with "could not reach Dropbox" and starts over from a fresh listing next time. Because the catch is
`Exception`, the browser's `kotlin.Error` ("Failed to fetch") is not even caught here.

The KDoc above `request` (`DropboxTransport.kt:79-81`) states the premise this rests on: "on a phone it is more often a
cell handover or a moment in a tunnel than a service that is gone … Anything else the transport throws is not retried."
A handover shows up as a reset at least as often as a timeout.

### Is a resend always safe? (checked at HEAD)

Every call that goes through `request`:

- `files/list_folder`, `list_folder/continue`, `users/get_current_account`, `delete_batch/check`, `files/download`:
  reads; resending changes nothing.
- `files/upload`, either mode: Dropbox's `WriteMode` documents that when "the target path refers to a file with
  identical contents, nothing gets written; no conflict" (Campfire never sets `strict_conflict`, which is what would
  turn that into a conflict). So a resend after a landed first attempt is answered with the file's metadata, and
  `upload` returns `Written` with the revision the first attempt created: exactly what a single successful attempt
  would have returned. Only if something else changed the file between the two attempts does the resend answer
  `path/conflict` → `Conflict`, which is the ordinary contested write: `SyncEngine.upload` marks it unresolved and the
  next pass decides from a fresh listing (`SyncPlanner` → `Resolve`, `isSameContent`), and `ConflictResolver.resolveWith`
  checks with a download whether what is there is its own write (`isOwnWrite`). The conflict copy's own `add` upload is
  the same: a resend of a landed one answers `Written`. No duplicate, no lost version. (Earlier drafts of this plan
  said a landed resend answers `Conflict`; it does not, and the outcome is safe either way.)
- `uploadDocument` (`preferences.json`): `SyncedPreferencesSync` answers `Conflict` by downloading the document again
  (`repeat(MAXIMUM_ATTEMPTS)`); the re-read now equals `merged`, so `if (merged == remoteDocument) return merged`.
- `files/delete_batch`: a resend after an accepted first job starts a second job; its entries for files the first job
  removed answer `path_lookup/not_found`, which `deleteBatch` treats as done, and one refused as busy while the first job
  runs is retried with the back-off. `parent_rev` keeps a re-created file safe (`path_write/conflict` is left alone).
- The token refresh is not inside the retried `try` (see plan 05); `revoke` and the authorization code exchange do not go
  through `request`.

So the claim holds: every call `request` sends is safe to send again.

### `navigator.onLine` on the web

Left out. The remote module has no expect/actual for it (its only one is `createHttpClient`), and
`app/web/CLAUDE.md` already records that `navigator.onLine` is not trusted (a captive network or a VPN reports online
while nothing gets through, some browsers report offline on a working LAN). A web run while offline gets a `kotlin.Error`
at once from `fetch`, which the bounded retry below turns into a failure within seconds anyway.

## Fix

In `DropboxTransport.request`, catch `Throwable` in the inner `try` (the browser engine throws `kotlin.Error`), keep the
`ensureActive()` first (it rethrows a cancellation of this coroutine), and retry every other failure, with its own limit:

```kotlin
} catch (throwable: Throwable) {
    currentCoroutineContext().ensureActive()
    val limit = if (throwable.isTimeout()) MAXIMUM_RETRIES else MAXIMUM_TRANSPORT_FAILURE_RETRIES
    if (attempt >= limit) throw throwable
    null
}
```

The wait stays `exponentialBackoffSeconds(attempt) * 1000L + jitter`, so the ladder is shared with the timeouts.

Options for the limit of the non-timeout failures:

- **A. The same six retries as a timeout.** Simplest. But a device that is plainly offline (DNS fails at once,
  `Failed to fetch` on the web) then sits "syncing" for about 94 s before saying it cannot reach Dropbox, on every
  automatic run.
- **B. Three retries (2 + 4 + 8 s, about 14 s), recommended.** A reset or a dropped stream is a moment of trouble;
  three tries over 14 s outlast a cell handover, and an offline device still hears within seconds. Name it
  `MAXIMUM_TRANSPORT_FAILURE_RETRIES = 3` in the companion, with a KDoc saying why it is shorter than
  `MAXIMUM_RETRIES`.

`isTimeout()` takes a `Throwable` receiver after this change. Everything `block` throws is the transport's (the Ktor
`post` saves the body inside the call, so a body cut short throws here too); a cancellation that is not this
coroutine's (a closed client) is retried like a reset, and on its last attempt reaches `transport`, which makes it a
`SyncNetworkException` as today.

Update the KDoc above `request`: replace the last paragraph with one that says a failure without an answer (a timeout,
a reset, a stream that ended early, or a fetch that failed) is retried with the same doubling wait, a timeout up to
`MAXIMUM_RETRIES` times and anything else up to `MAXIMUM_TRANSPORT_FAILURE_RETRIES`, because a cell handover shows up as
any of them, and that every call sent here is safe to send again (Dropbox writes nothing for an upload whose bytes are already the
file's and answers its metadata, so a resent write that had landed reads as written; one that meets a file changed in
between is a `Conflict` the engine resolves as usual; a resent deletion finds the file gone).

Update `data/source/remote/implementation/CLAUDE.md`, Dropbox section, the sentence "Of transport failures only timeouts
are retried; any other ends the run as `SyncNetworkException`." to: "Transport failures are retried too: timeouts six
times, anything else (a reset, a cut stream, or a failed `fetch`) three, since every call here is safe to resend."

## Tests

In `DropboxRequestTest` (MockEngine, virtual time):

- `a connection reset is retried`: the handler throws `kotlinx.io.IOException("Connection reset")` on the first two
  attempts, then answers a one-file listing; `provider.list()` returns that file.
- `a failure that is not a timeout is given up on after a few attempts`: the handler always throws
  `Error("Fail to fetch")`; `assertFailsWith<SyncNetworkException> { provider.list() }`, the attempts counted are 4 (the
  retry count is the behaviour), and `currentTime` is below 20 s.
- `an upload whose first attempt was cut short is sent again`: first attempt throws `IOException`, second answers a
  receipt; `upload` returns `Written`.
- Keep `a socket timeout on every attempt is the service not being reached` (7 attempts) and the browser test
  `a request the browser could not send is the service not being reached` unchanged; they must still pass.
