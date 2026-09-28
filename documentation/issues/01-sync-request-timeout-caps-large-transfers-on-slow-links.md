# Stop the 60 s request timeout from ending every sync run of a large library on a slow connection

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/HttpClientConfiguration.kt, data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt, data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt (or the nearest existing test of `request()`), data/source/remote/implementation/CLAUDE.md

## Problem
The one Ktor client caps every request at 60 s end to end (`HttpClientConfiguration.kt:30-38`:
`requestTimeoutMillis = 60_000`, `socketTimeoutMillis = 60_000`, `connectTimeoutMillis = 20_000`). The request
timeout is a bound on the whole exchange, headers and body included, not on inactivity, so a transfer that is
flowing but slow is cut off at 60 s however well it is going.

`DropboxSyncProvider.request()` (`DropboxSyncProvider.kt:393-421`) retries only an HTTP *answer* that says to wait
(429, 5xx, 409 `too_many_write_operations`). A timeout never produces an answer: Ktor throws, `transport {}`
(`DropboxSyncProvider.kt:439-461`) turns the throwable into a `SyncNetworkException`, and that is one of the
exceptions that end the whole run (`SyncEngine.kt:453-457`, `endsTheRun`). So one slow request ends the run.

Where that bites:
- Every run begins with `files/list_folder` and its `continue` pages (`DropboxSyncProvider.kt:209-220`), and a page
  of a folder of a few thousand songs is several hundred KB to a megabyte of JSON. On a 2G or congested connection
  (100 kbit/s and worse is common on the move) a page takes longer than 60 s. The run then fails before anything has
  moved, and fails the same way at the next launch, ten seconds after the next edit, and on every "Sync now": a
  user with a large library and a poor connection **can never complete a sync**, and Settings only ever shows the
  generic network failure.
- A single large song (a few hundred KB of tabs is not unusual) uploaded or downloaded over such a link fails the
  same way, and with it the run.
- The cover download shares the client, so a slow cover host makes the cover `Unreachable` and blocks it for a
  minute (`CoverArtRepositoryImpl`), which is harmless but the same shape.

A retry alone does not fix it: a request that deterministically needs 90 s fails on every attempt. The bound is the
problem.

## Fix
1. In `configureClient`, keep the connect timeout and the socket timeout (inactivity between packets, which is what
   catches a dead connection) and drop the total request timeout for the engines that honour the socket timeout
   (OkHttp, Darwin, CIO): `requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS`. The browser engine has no
   socket timeout (fetch offers none), so keep a request timeout there, but a generous one (10 minutes), passed in
   the same way `sendsUserAgent` is: the wasmJs `HttpClient` builder is the one place that knows it is in the
   browser.
2. Optionally, since a `list_folder` page and an upload have known sizes, give the sync calls a per-request bound
   proportional to the size instead of none: `request.timeout { requestTimeoutMillis = BASE + bytes / SLOW_BYTES_PER_SECOND }`
   with `SLOW_BYTES_PER_SECOND` around 4 KB/s. This is a refinement; step 1 alone removes the failure.
3. In `request()`, treat a Ktor timeout (`HttpRequestTimeoutException`, `ConnectTimeoutException`,
   `SocketTimeoutException`) like a 5xx: retry with the existing back-off up to `MAXIMUM_RETRIES`, then let
   `transport {}` turn it into `SyncNetworkException` as now. Catch them around `block(token)` inside the loop so
   that `transport {}`'s conversion still covers the last attempt. A stalled connection on a mobile link is often
   transient (a cell handover), and the run should not end on the first one.
4. Update the retry paragraph of `data/source/remote/implementation/CLAUDE.md` (it currently says the retry is keyed
   off the status alone) and the timeout paragraph, if any.

## Verification
- `DropboxRequestTest` (MockEngine in virtual time): an engine whose handler throws `SocketTimeoutException` on the
  first two attempts and answers 200 on the third makes `list()` succeed; one that throws on every attempt ends in
  `SyncNetworkException` after `MAXIMUM_RETRIES`.
- `./gradlew :data:source:remote:implementation:desktopTest`.
- Manual: on a Mac with Network Link Conditioner set to "Edge" (or an Android emulator on the GPRS profile) and a
  connected account holding about 2000 songs, "Sync now" completes rather than failing with the network error.

## Conflicts
`request()` and `transport()` are the whole of Dropbox's error handling; nothing else in this batch touches them.
