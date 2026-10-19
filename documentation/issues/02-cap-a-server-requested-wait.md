<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give up rather than wait when Dropbox or MusicBrainz asks for a wait longer than a cap

**Challenged:** sound — one note: `MAXIMUM_RETRY_SECONDS` is `private` in `network/Backoff.kt`, so MusicBrainz's 32 s cap is its own constant (or that one becomes `internal`). With plan 13, a run ended here is retried after 1, 2, 5… minutes whatever Dropbox asked, one listing each; acceptable, and the completed files of the ended run stay indexed (`finishRunCutShort`).

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/musicBrainz/MusicBrainzRemoteSource.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/musicBrainz/MusicBrainzRemoteSourceTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxTransport.retryAfterMillis` (`dropbox/DropboxTransport.kt:121-129`) waits whatever the answer names:

```kotlin
status == HttpStatusCode.TooManyRequests ||
    status.value >= 500 ||
    (status == HttpStatusCode.Conflict && errorSummary().contains("too_many_write_operations")) ->
    (retryAfterSecondsInBody() ?: headers["Retry-After"]?.toLongOrNull()
        ?: exponentialBackoffSeconds(attempt)) * 1000L
```

and `request` does `delay(retryAfterMillis + Random.nextLong(RETRY_JITTER_MILLIS))` with no upper bound. Probe:
`Retry-After: 86400` was waited 86,400 s; Settings showed "syncing" for the whole day, and no other run can start while
it waits. Any proxy, CDN error page, or misbehaving middlebox that adds a large `Retry-After` to a 5xx does this. A
negative value (`-5`) parses too and is waited as nothing, which is harmless but should be clamped.

`MusicBrainzRemoteSource.fetch` (`musicBrainz/MusicBrainzRemoteSource.kt:71` and `:78`) does the same for the catalogue
search a person is watching:

```kotlin
Triple(response.status, response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(), response.bodyAsText())
…
delay((retryAfterSeconds ?: exponentialBackoffSeconds(attempt)) * 1000L)
```

so the cover search or details lookup sits on "MusicBrainz is busy, trying again in a moment…" for as long as the
header says, and iTunes' results are the only thing that ever arrives.

## Fix

### Dropbox

- Split the server's wait from the back-off: compute `val requested = retryAfterSecondsInBody() ?: headers["Retry-After"]?.toLongOrNull()`
  and use `exponentialBackoffSeconds(attempt)` only when it is null. Clamp `requested` at zero.
- When `requested > MAXIMUM_REQUESTED_WAIT_SECONDS`, do not wait: throw
  `SyncNetworkException("Dropbox asked to wait $requested s.")` from `request`. Thrown from `request` it is a run-ending
  failure whatever the call (also for the 409 `too_many_write_operations` of an upload, which otherwise ends as one
  file's `DropboxApiException`), and the run's outcome reads as a network failure, Dropbox's "busy".
- **Recommended cap: 120 s.** Dropbox's own `retry_after` for rate limits is seconds to a few tens of seconds in practice
  (the tests use 10); two minutes is longer than the whole doubling ladder's longest single wait (32 s) by a margin, so
  an honest request is still honoured, while a run never sits silent for longer than that. A run ended here is retried
  by the next scheduled or manual run.
- Keep the jitter on top of an honoured wait.

The simplest shape: let `retryAfterMillis` return a small result (`null` = act on it; a wait in ms; or throw directly
from it, since it is only called from `request`). Throwing from inside `retryAfterMillis` is fine; it is outside
`transport`, and `SyncNetworkException` is what `transport` would make of it anyway.

### MusicBrainz

- Same split and clamp. When the header asks for more than `MAXIMUM_REQUESTED_WAIT_SECONDS`, throw
  `CatalogueSearchException("$SERVICE_NAME asked to wait $retryAfterSeconds s.")` at once, without calling `onBusy`;
  the search reports MusicBrainz as failed like any other catalogue failure, and iTunes' results stay.
- **Recommended cap: 32 s** (the doubling's own maximum, `MAXIMUM_RETRY_SECONDS` in `network/Backoff.kt`): a person is
  looking at the sheet; a wait the app would never choose itself is not one to sit through on the server's word.
  Five retries of up to 32 s each already bound the whole wait at under three minutes.

Name both caps in their companions with a one-line KDoc (why that number). Update
`data/source/remote/implementation/CLAUDE.md`: in the Dropbox "Retries on 429, 5xx …" bullet add "a requested wait over
120 s ends the run as `SyncNetworkException` instead"; in the MusicBrainz "Pace" bullet add "a `Retry-After` over 32 s
fails the search at once".

## Tests

- `DropboxRequestTest`: `a wait longer than the cap ends the run instead of being waited`: the handler answers 503 with
  `Retry-After: 86400`; `assertFailsWith<SyncNetworkException> { provider.list() }` and `currentTime < 1_000`.
  Same with the body form (`{"error":{"retry_after":86400}}`, 429).
- `DropboxRequestTest`: `a wait under the cap is waited`: `Retry-After: 60` once, then a listing; `list()` succeeds and
  `currentTime >= 60_000`.
- `MusicBrainzRemoteSourceTest` (beside the existing `Retry-After: 2` test at line ~123): `Retry-After: 3600` →
  `assertFailsWith<CatalogueSearchException>`, `currentTime < 1_000`, and `onBusy` was not called.
