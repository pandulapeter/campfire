<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Bound each cover download to a minute, so four stalled covers cannot hold back every other one on the web

**Challenged:** amended — the test now builds its client and engine on the test scheduler (as
`MusicBrainzRemoteSourceTest` does), so the minute is virtual time; the fix itself is unchanged.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** web (all, in a milder form)
**Lane:** C
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceImpl.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`network/HttpClientConfiguration.kt:39` and `:46` give the browser one bound for every request, 10 minutes in total
(`BROWSER_REQUEST_TIMEOUT_MILLIS = 10 * 60_000L`), since `fetch` has no inactivity timeout. `CoverArtRepositoryImpl`
lets at most `MAX_CONCURRENT_DOWNLOADS = 4` downloads reach the network (`CoverArtRepositoryImpl.kt:166`), and its KDoc
says "One that has started is carried to its end whoever is still waiting". Four cover hosts that accept the connection
and then send nothing (a host behind a stalled proxy, a dead CDN edge) hold all four slots for up to ten minutes; every
other cover in the list, the cover search's thumbnails included, waits behind them. Natively the socket timeout ends such
a stall after 60 s of silence, so the same four hold the slots for a minute.

Checked at HEAD: `HttpTimeout` is installed on every engine by `configureClient` (`HttpClientConfiguration.kt:38-42`),
so a per-request `timeout { }` takes effect everywhere. In Ktor 3.6.0 (`gradle/libs.versions.toml`, `ktor = "3.6.0"`)
`HttpRequestTimeoutException` extends `kotlinx.io.IOException`, not `CancellationException`
(`io/ktor/client/plugins/HttpTimeout.kt`); the request is cancelled with it as the cause. Either way it reaches one of
`downloadCoverArt`'s two catches: as itself, the `Throwable` branch (→ `Unreachable`); as a `CancellationException`
of the call, the first branch, where `currentCoroutineContext().ensureActive()` passes (this coroutine was not cancelled)
and the answer is `Unreachable`. No change to the catches is needed.

## Fix

Add `timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS }` to the `prepareGet(url) { … }` in
`CoverArtRemoteSourceImpl.downloadCoverArt`. The request timeout covers the whole `execute { }` block, the body read
included. A timed-out cover is `Unreachable`, asked again after a minute.

A per-request bound applies on every engine, so it must not cut a legitimate cover on a slow native link. Covers are
almost all 250 px thumbnails (the Cover Art Archive's `front-250`, iTunes' `250x250bb.jpg`): 15–40 KB. Only an address
typed or pasted by hand can be up to `MAXIMUM_SIZE_BYTES` (5 MB). Options:

- **A. 60 s total on every engine (recommended).** A thumbnail needs under 1 KB/s to make it; 5 MB needs ~85 KB/s,
  which 3G gives. Matches the native silence bound, so a stall costs a slot for the same minute everywhere, and matches
  the repository's one-minute memory of an unreachable address. Cost: a multi-megabyte cover by hand on a slow native
  link that today finishes in two or three minutes is cut and never loads on that link (it is retried a minute later
  and cut again). A song's cover from the search is never that large.
- **B. 30 s total.** Frees slots faster; 5 MB then needs ~170 KB/s, and thumbnails on a congested 2G link are at risk.
  Not recommended.
- **C. An inactivity bound of its own** (read the body in chunks, each `readAvailable` inside a `withTimeout`), plus a
  bound on the wait for headers. Lets any slow transfer finish while a stall ends; but the time to the first byte still
  needs a total on the web, and it is twice the code for a case (huge covers on slow links) that barely exists. Not
  recommended.
- **D. A browser-only bound** through lane T plan 03's request attribute. Would leave native untouched, but couples the
  two lanes (plan 03 may not have landed when this runs). Possible as a follow-up once both lanes are in, if A's native
  cost ever shows.

Name the constant in the companion (`DOWNLOAD_TIMEOUT_MILLIS = 60_000L`) with a KDoc saying what it bounds and why that
number (a thumbnail at any rate a phone gets, 5 MB at ~85 KB/s; a stall must not hold one of the repository's four
slots for the browser's ten minutes).

Update `data/source/remote/implementation/CLAUDE.md`, "Downloads", cover bullet: "at most a minute per download, so a
stalled host cannot hold the repository's download slots".

## Tests

`CoverArtRemoteSourceTest`: `a cover that never arrives is unreachable after a minute`: the test client installs
`HttpTimeout` (a per-request `timeout` needs the plugin, which production installs in `configureClient`), the handler
`awaitCancellation()`s; `downloadCoverArt(URL)` returns `CoverArtDownload.Unreachable` and `currentTime` is at least
60 s and well under 10 minutes (`runTest`'s virtual time). The other tests keep passing with the plugin installed in the
shared `source()` helper.

Every clock in that test must be the test scheduler's: build the client the way `MusicBrainzRemoteSourceTest.source`
does, `HttpClientHolder(dispatcher = StandardTestDispatcher(testScheduler)) { HttpClient(MockEngine.create { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }) { … } }`
(the helper becomes a `TestScope` extension). With `MockEngine(handler)`'s own dispatcher the timeout's `delay` and the
handler can run on a real thread, so the test either waits a real minute or reads `currentTime` as 0.
