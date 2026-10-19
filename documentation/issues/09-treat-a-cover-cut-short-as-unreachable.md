<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Treat a cover that arrives shorter than its declared length as unreachable rather than keep it

**Challenged:** sound

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (seen on desktop, CIO)
**Lane:** C
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceImpl.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/importDownload/ImportDownloadRemoteSourceImpl.kt`,
a new `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/DeclaredLength.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/importDownload/ImportDownloadRemoteSourceTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`coverArt/CoverArtRemoteSourceImpl.kt:48-53`:

```kotlin
(response.contentLength() ?: 0) > MAXIMUM_SIZE_BYTES -> CoverArtDownload.Missing
else -> {
    // A length is not promised, so the body is read one byte past the limit to find out.
    val bytes = response.bodyAsChannel().readBuffer(MAXIMUM_SIZE_BYTES + 1L).readByteArray()
    if (bytes.isEmpty() || bytes.size > MAXIMUM_SIZE_BYTES) CoverArtDownload.Missing else CoverArtDownload.Image(bytes)
}
```

`readBuffer` stops at the end of the channel, wherever that came. Probe on CIO (desktop): `Content-Length: 1000`, 500
bytes sent, socket closed → `Image(500 bytes)`. `CoverArtRepositoryImpl.fetch` saves it in `covers/<sha>`, and from then
on the device's copy is answered first, forever: a cover half grey (a JPEG decoders draw up to where it stops), or
nothing (Coil fails to decode a cut PNG, and the row stays empty with no further attempt, since the copy exists). Only
**Settings → Library → clear the cover cache** mends it. A connection dropped mid-cover on a train is the usual cause.

`importDownload/ImportDownloadRemoteSourceImpl.kt:46-49` has the same pattern (`readBuffer(maxSizeBytes + 1)`), where a
cut file would be handed to the import (reached by nothing in the app today, per the module's `CLAUDE.md`).

### The declared length is the *encoded* length

The client installs no `ContentEncoding` plugin (`network/HttpClientConfiguration.kt`), but engines decode on their
own:

- OkHttp (Android) adds `Accept-Encoding: gzip` itself, decodes, and then strips both `Content-Encoding` and
  `Content-Length` from what Ktor sees: no length, nothing to compare.
- CIO (desktop) sends no `Accept-Encoding`, so bodies arrive unencoded: the length is the body's.
- NSURLSession (iOS) and `fetch` (web) decode transparently and keep the compressed `Content-Length`. On the web,
  `Content-Encoding` is not a CORS-safelisted response header, so a cross-origin answer (every cover) hides it unless the
  host exposes it, while `Content-Length` is safelisted and visible.

So `bytes.size != contentLength` would fail every gzip-served cover on iOS and the web. Images are rarely gzipped, but
SVG (`image/svg+xml` matches `ContentType.Image.Any`) and misconfigured hosts are; and gzip of an already compressed
JPEG makes it slightly *larger*, so its decoded body is a little shorter than the declared length.

## Fix

Add `network/DeclaredLength.kt` with one internal function used by both sources, e.g.
`internal fun HttpResponse.isCutShort(received: Int): Boolean`:

- `false` when there is no `Content-Length`;
- `false` when a `Content-Encoding` header other than `identity` is visible (the length is of the encoded body);
- otherwise `true` when `received < declared - slack`, with `slack = declared / 512 + 64`. The slack covers gzip's
  worst-case expansion of incompressible data (header and trailer ~18 bytes, ~5 bytes per 16–64 KB stored block, far
  under 0.2%) for an encoded body whose encoding the web cannot see, and a body cut short is nearly always cut by far
  more. Only *shorter* counts: a decoded body longer than the encoded length is the ordinary gzip case.

In `CoverArtRemoteSourceImpl`, after reading: `bytes.size > MAXIMUM_SIZE_BYTES` → `Missing` as now; empty → `Missing` as
now; `response.isCutShort(bytes.size)` → `CoverArtDownload.Unreachable` (the repository then asks again after a minute
and writes nothing). In `ImportDownloadRemoteSourceImpl`, the same check → `ImportDownload.Unreachable`; include it,
since the helper is shared and the import would otherwise hand a cut archive or PDF to the readers whenever it is wired.

The KDoc of the helper states the encoded-length reasoning above in two or three sentences; the comment
"A length is not promised, so the body is read one byte past the limit to find out" gains "and a declared one the body
falls short of is a transfer cut off, not a smaller image".

**End marker check (JPEG `FF D9`, PNG `IEND`): not recommended.** It would also catch a body cut without any declared
length, but JPEGs with bytes after the end marker (camera thumbnails, padding) are common enough that a correct cover
would be refused as unreachable and retried every minute forever; and GIF, WebP, and SVG would each need their own
rule. A cut without a declared length is rare on the hosts covers come from (the Cover Art Archive and Apple's CDN
declare lengths).

Update `data/source/remote/implementation/CLAUDE.md`, "Downloads": cover bullet gains "a body shorter than an unencoded
declared length → `Unreachable`"; the import bullet likewise.

## Tests

`CoverArtRemoteSourceTest` (MockEngine passes the given headers through unchanged):

- `a cover cut short of its declared length is unreachable`: `respond(ByteArray(500), OK, headersOf(ContentType to image/jpeg, ContentLength to "1000"))`
  → `CoverArtDownload.Unreachable`.
- `a cover served encoded is kept although its length was the encoded one`: 1,000 bytes, `Content-Length: 900`,
  `Content-Encoding: gzip` → `Image`; and 1,000 bytes, `Content-Length: 1010`, no `Content-Encoding` (the web hiding it)
  → `Image`.
- `a cover of exactly its declared length is kept` → `Image`.

`ImportDownloadRemoteSourceTest`: 500 of a declared 1,000 bytes → `ImportDownload.Unreachable`.

If MockEngine ever rejects a `Content-Length` that differs from the body, build the body as
`ByteReadChannel(bytes)` with `respond(content = channel, …)`.
