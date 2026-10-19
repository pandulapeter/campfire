<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Count a Dropbox call's requested waits and its failures without an answer apart, so rate limiting does not use up the retries of a connection reset

**Challenged:** sound

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T (after plan 01, same worktree)
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxTransport.request` (`dropbox/DropboxTransport.kt`) keeps one `attempt` counter for two different budgets:

```kotlin
var attempt = 0
...
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            val cause = (throwable as? SyncNetworkException)?.cause ?: throwable
            val limit = if (cause.isTimeout()) MAXIMUM_RETRIES else MAXIMUM_TRANSPORT_FAILURE_RETRIES
            if (attempt >= limit) throw throwable
            null
        }
    }
    if (response == null) {
        delay(exponentialBackoffSeconds(attempt) * 1000L + Random.nextLong(RETRY_JITTER_MILLIS))
        attempt++
        continue
    }
    ...
    val retryAfterMillis = response.retryAfterMillis(attempt)
    if (retryAfterMillis == null || attempt >= MAXIMUM_RETRIES) return response
    attempt++
    delay(retryAfterMillis + Random.nextLong(RETRY_JITTER_MILLIS))
```

The waits Dropbox asks for (429, 408, 5xx, 409 `too_many_write_operations`; up to `MAXIMUM_RETRIES` = 6) and the
failures without an answer (a timeout up to `MAXIMUM_RETRIES`, anything else up to
`MAXIMUM_TRANSPORT_FAILURE_RETRIES` = 3) draw on the same count. After three 429s, which a first sync is expected to
meet (the KDoc: "being rate limited is the expected answer"), `attempt` is 3, and one connection reset then meets
`attempt >= MAXIMUM_TRANSPORT_FAILURE_RETRIES` and ends the run with no retry at all. Proven at HEAD with `MockEngine`:
three 429s, then an `IOException("Connection reset")`, then a good answer → `list()` throws `SyncNetworkException:
Connection reset` after 4 requests. The other way round, three resets leave only three of the six waits for a rate
limit. And the back-off starts from the inflated count: a reset after three 429s would wait `exponentialBackoffSeconds(3)`
= 16 s rather than 2 s. Neither the KDoc above `request` ("a timeout up to [MAXIMUM_RETRIES] times and anything else
up to [MAXIMUM_TRANSPORT_FAILURE_RETRIES]") nor `data/source/remote/implementation/CLAUDE.md` ("six retries … timeouts
six times, anything else … three") is true of a call that meets both.

`DropboxSyncProvider.deleteBatch` (`dropbox/DropboxSyncProvider.kt`, the loop with
`delay(exponentialBackoffSeconds(attempt) * 1000L + …)`) was checked and does **not** have the pattern: its `attempt`
counts only rounds of entries refused as `too_many_write_operations`, one kind; the transport failures and waits of
each `delete_batch` / `delete_batch/check` call are retried inside `requests.rpc` → `request`, per call, and fixed
there. It is left unchanged.

## Fix

Replace `attempt` with two counters in `request`:

```kotlin
var waits = 0          // waits Dropbox asked for, see retryAfterMillis
var failures = 0       // failures without an answer
```

- Failure branch: `if (failures >= limit) throw throwable`; then
  `delay(exponentialBackoffSeconds(failures) * 1000L + Random.nextLong(RETRY_JITTER_MILLIS))` and `failures++`.
- Answer branch: `val retryAfterMillis = response.retryAfterMillis(waits)`;
  `if (retryAfterMillis == null || waits >= MAXIMUM_RETRIES) return response`; `waits++`.
- The 401 branch is unchanged (one renewal per call, counted by `refusedToken`).
- Timeouts and other failures without an answer keep sharing `failures` (each checked against its own limit, as now):
  they are one budget, "the network did not answer", and the limits stay those the constants' KDocs describe. With
  plan 01 in place, a renewal that failed for the network (one's own or one waited for) counts here too.
- No reset of either counter on progress: the bounds stay simple totals per call. The worst case of one call grows
  from the larger of the two budgets to their sum (six waits of at most 120 s each, plus seven attempts bounded by the
  engine's timeouts); say so in the KDoc.
- KDoc above `request`: in the paragraph on failures without an answer, add that they are counted apart from the
  waits Dropbox asks for, so a call rate limited a few times still gets every retry of a reset, and the reverse.
  `MAXIMUM_RETRIES`'s KDoc ("Six waits of 2, 4, 8, 16, 32 and 32 seconds") stays true per budget.
- `data/source/remote/implementation/CLAUDE.md`, the "Retries on 408, 429, 5xx …" bullet in "Dropbox (`dropbox/`)":
  after "Transport failures are retried too: timeouts six times, anything else (a reset, a cut stream, or a failed
  `fetch`) three", add "counted apart from the waits," (or equivalent), so the totals read as per-budget.

## Tests

In `DropboxRequestTest.kt` (`./gradlew :data:source:remote:implementation:desktopTest`), with `dropboxProvider` on
its default engine dispatcher (no `HttpTimeout` bound is involved; the waits are `delay`s on the test's virtual time):

1. **`a connection reset after being rate limited is still retried`** — handler by request count: requests 1–3 answer
   `respondJson("""{"error_summary":"too_many_requests/"}""", HttpStatusCode.TooManyRequests)` (no `retry_after`, so
   the doubling applies), request 4 throws `kotlinx.io.IOException("Connection reset")`, request 5 answers
   `EMPTY_LISTING`. Assert `provider.list().files` is empty (it succeeded; HEAD throws after request 4) and
   `currentTime in 16_000L..<18_000L`: waits of 2 + 4 + 8 s, then 2 s for the reset from its own count, plus at most
   4 × 0.5 s jitter (HEAD's inflated count would wait 16 s for the reset).
2. **`a call that met connection resets still gets every wait it is asked for`** — requests 1–3 throw
   `IOException("Connection reset")`, requests 4–9 answer 429 as above, request 10 answers `EMPTY_LISTING`. Assert
   success and 10 requests (HEAD returns the 429 at request 7 and fails as `Dropbox is busy`).

The existing `gives up on a service that stays unavailable`, `waits longer every time the service is unavailable without
saying for how long` (still about 94 s), `a socket timeout on every attempt is the service not being reached` (still
7 attempts), `a failure that is not a timeout is given up on after a few attempts` (still 4), and
`DropboxTokensTest`'s `a renewal that never answers ends the run in bounded time` must pass unchanged.
