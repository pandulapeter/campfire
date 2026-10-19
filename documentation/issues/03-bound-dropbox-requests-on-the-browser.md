<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Bound each Dropbox call on the browser by what it carries, not by one ten-minute limit

**Challenged:** amended — `HttpTimeout`'s request timeout runs on the engine's dispatcher, so under `MockEngine` it waits in real time (probe: a 30 s bound took 30 real seconds, `currentTime` 0) and the tests as written would hang past `runTest`'s one-minute limit; the tests now give the mock engine the test dispatcher (`DropboxTestService.kt` added to Files) and check each call's bound directly from its `HttpTimeoutCapability`.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** web
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/HttpClientConfiguration.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTestService.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`network/HttpClientConfiguration.kt:39` and `:46`:

```kotlin
requestTimeoutMillis = if (hasSocketTimeout) HttpTimeoutConfig.INFINITE_TIMEOUT_MS else BROWSER_REQUEST_TIMEOUT_MILLIS
…
private const val BROWSER_REQUEST_TIMEOUT_MILLIS = 10 * 60_000L
```

`fetch` has no inactivity timeout, so on the web this total is the only bound on a stalled call, and
`DropboxTransport.request` retries an `HttpRequestTimeoutException` as a timeout, six times. A web run whose connection
went silent (a laptop's Wi-Fi that dropped behind a proxy, a stalled HTTP/2 stream) takes 7 × 600 s + 94 s of back-off ≈
71.6 minutes before it fails, showing "syncing" throughout. Natively the same stall costs 7 × 60 s (the socket
inactivity timeout) + 94 s ≈ 8.6 minutes.

The calls differ widely in what they carry:

- `users/get_current_account`, `files/delete_batch`, `delete_batch/check`: a few hundred bytes each way.
- `files/list_folder`, `list_folder/continue`: one page. Dropbox does not document the page size; observed pages hold up
  to about 2,000 entries of ~450 bytes of JSON, so up to ~1 MB raw, and the browser always asks for and decodes gzip,
  which brings that to roughly 150–200 KB on the wire.
- `files/download` / `files/upload`: one library file. Usually a few KB, but up to `SyncEngine.MAXIMUM_FILE_SIZE`
  (`ImportLimits.MAX_TEXT_FILE_SIZE`, 8 MB).

## Fix

A per-request `timeout { requestTimeoutMillis = … }` applies on **every** engine, replacing the native
`INFINITE_TIMEOUT_MS` total. That would shorten native wrongly: today a native transfer may take as long as it likes
while data keeps arriving (an 8 MB file on a 6 KB/s 2G link takes 20+ minutes and finishes). So the bound must apply on
the browser only. Options:

- **A. Browser-only call bounds through a request attribute (recommended).** In `HttpClientConfiguration.kt` add an
  `AttributeKey<Long>` (e.g. `BrowserCallTimeout`) and a small client plugin installed only when `!hasSocketTimeout`:

  ```kotlin
  private val BrowserCallTimeouts = createClientPlugin("BrowserCallTimeouts") {
      onRequest { request, _ ->
          request.attributes.getOrNull(BrowserCallTimeout)?.let { bound -> request.timeout { requestTimeoutMillis = bound } }
      }
  }
  ```

  `onRequest` runs before `HttpTimeout`'s `Send` hook, which then fills connect and socket from the client's defaults
  (`HttpTimeout.kt` in Ktor 3.6.0: `this.connectTimeoutMillis = this.connectTimeoutMillis ?: connectTimeoutMillis`). A
  request without the attribute (covers, catalogue searches, the import download) keeps the 10-minute total, so this
  plan does not touch them. On native the plugin is not installed and the attribute is ignored, so nothing changes
  there.
- **B. A shorter browser default plus a longer per-request bound for listings.** Lowers every browser request, covers
  and catalogues included (out of this plan's scope, and lane C plan 11 owns covers), and the listing's per-request
  bound would apply natively as well. Not recommended.
- **C. An `expect val` saying whether the engine has socket timeouts, read by `DropboxTransport`.** Works, but adds a
  platform declaration for something `configureClient` already knows. Not recommended.

With A, the Dropbox requests put the attribute on: `attributes.put(BrowserCallTimeout, …)` inside the request builders
in `DropboxTransport.rpc`, `DropboxSyncProvider.downloadResponse`, and `DropboxSyncProvider.upload`. Recommended values
(constants in `DropboxTransport`'s companion, each with a one-line KDoc):

| Call | Bound | Why |
|---|---|---|
| RPC: account, `delete_batch`, `delete_batch/check` | 60 s | A few hundred bytes; matches the native silence bound. |
| `list_folder`, `list_folder/continue` | 120 s | A gzipped page of ~200 KB at ~2 KB/s. `rpc` takes the bound as a parameter, or the listing calls pass a longer one. |
| `files/download` | 120 s | Size is not known when the request is built (`SyncFolder.download` takes none); fits a song at any rate and 8 MB at ~70 KB/s. |
| `files/upload` | 60 s + `bytes.size / 16_384` s | Size is known; a 5 KB song gets 60 s, 8 MB gets ~9 minutes. |

After this a stalled web RPC fails in about 7 × 60 + 94 s ≈ 8.6 minutes (as natively) and a stalled listing page or
download in about 15.6 minutes, rather than 71.6. A large file on a very slow browser link can now time out where it
finished before; the KDoc of the constants says so, and that the native engines are unaffected.

Update the KDoc of `configureClient` (the `hasSocketTimeout` paragraph: the browser keeps `BROWSER_REQUEST_TIMEOUT_MILLIS`
as the default and takes a shorter one for any request carrying the attribute) and the
`data/source/remote/implementation/CLAUDE.md` Dropbox "Timeouts" bullet: "The browser has a 10-minute total, since `fetch`
has no inactivity timeout; Dropbox calls carry a shorter one of their own there (60 s RPC, 120 s listing pages and
downloads, uploads by size)."

If plan 01 has landed, `HttpRequestTimeoutException` is still a timeout (full six retries); nothing here depends on 01.

## Tests

`HttpTimeout`'s request bound is a `delay` on the engine's dispatcher, not the caller's, so with a plain
`MockEngine(handler)` it passes in real time (checked: a 30 s bound took 30 real seconds while `currentTime` stayed 0).
Give `dropboxProvider` in `DropboxTestService.kt` an optional engine dispatcher, built as
`MockEngine(MockEngineConfig().apply { dispatcher = engineDispatcher; addHandler(handler) })`, and pass
`StandardTestDispatcher(testScheduler)` where a test needs a bound to expire; then it expires in virtual time (checked).

In `DropboxRequestTest`, with the production configuration on the mock client
(`dropboxProvider(configure = { configureClient(sendsUserAgent = false, hasSocketTimeout = false) }) { … }`):

- `each Dropbox call on the browser carries its own bound`: the handler records
  `request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis` per URL and answers normally; an account
  read records 60 s, a listing page 120 s, a download 120 s, and an upload of N bytes 60 s + N / 16,384 s. The same
  calls with `hasSocketTimeout = true` record `INFINITE_TIMEOUT_MS` (the attribute is ignored natively).
- `a stalled call on the browser fails within its own bound` (engine on the test dispatcher): the handler
  `awaitCancellation()`s; `loadAccount()` falls back to the stored account with `currentTime` below 10 minutes (today
  ~71), and `list()` throws `SyncNetworkException` with `currentTime` below 17 minutes.
- `a slow call on a native engine is not cut short` (engine on the test dispatcher): with
  `configureClient(sendsUserAgent = false, hasSocketTimeout = true)`, the handler `delay(5.minutes)` and then answers a
  listing; `list()` succeeds.

`configureClient` is `internal` in the same module, so the tests can call it.
