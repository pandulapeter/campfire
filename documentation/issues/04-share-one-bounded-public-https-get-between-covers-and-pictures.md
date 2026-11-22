# Download covers and linked pictures through one bounded public-https GET

**Kind:** duplication  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the table holds at HEAD; the streamed download's `refusalOf` and `failureOf` now come from the shared file too (the plan left a second copy of the status rule there); the two lambdas became a `Boolean` and two strings on `Body`; the empty-body-with-a-declared-length row is named as kept.
**Files:**
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/BoundedDownload.kt` (new)
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceImpl.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/importDownload/ImportDownloadRemoteSourceImpl.kt`
- Tests: `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceTest.kt`, `.../importDownload/PictureDownloadTest.kt`, `.../importDownload/ImportDownloadRemoteSourceTest.kt` and `StreamedImportDownloadTest.kt` (the streamed path's refusals, unchanged), `.../network/BoundedDownloadTest.kt` (new, only if the callers leave a rule unpinned)
- Docs: `data/source/remote/implementation/CLAUDE.md` (§ Downloads)

## Problem

`remote/implementation/CLAUDE.md` § Downloads describes one rule for a cover and a picture named by its link: every
hop a public `https` host, nothing read past the ceiling, 408/429/5xx, a transport failure, refused CORS, or a short
body `Unreachable`, any other refusal `Missing`. The code writes it twice:

- `CoverArtRemoteSourceImpl.downloadCoverArt`: `parseUrl(url)?.takeIf(::isPublicHttpsAddress)`, `prepareGet` with
  `attributes.put(PublicHostsOnly, Unit)` and a timeout, then the status, Content-Length, read
  `readBuffer(MAXIMUM_SIZE_BYTES + 1L)`, empty / too large / `isCutShort` checks, and `catch` blocks for
  `RefusedAddressException`, `CancellationException` (`currentCoroutineContext().ensureActive()`), and `Throwable`.
- `ImportDownloadRemoteSourceImpl.downloadPicture` → `fetch` + `refusalOf` + `failureOf`: the same steps again.

Both declare the same private extension, word for word:

```kotlin
/** The answers that say "not now" rather than "not here", which a later attempt may well get past. */
private val HttpStatusCode.isTransient
    get() = this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests || value >= 500
```

A fix to one (a new transient status, a CORS case on the web) has to be remembered in the other. Getting a cover's
split wrong is costly: the repository remembers a `Missing` cover as "no cover" for good, so a transient failure that
came out as `Missing` would never be retried.

What each caller maps today, which the fix must keep exactly:

| Outcome | Cover (`CoverArtDownload`) | Picture (`ImportDownload`) |
|---|---|---|
| URL does not parse or is not public `https` | `Missing`, no request | `Missing`, no request |
| status 408, 429, 5xx | `Unreachable` | `Unreachable` |
| other non-2xx | `Missing` | `Missing` |
| content type not `image/*` | `Missing` | (not checked) |
| Content-Length > ceiling | `Missing` | `TooLarge` |
| body empty | `Missing` | `Missing` |
| body > ceiling | `Missing` | `TooLarge` |
| body shorter than the declared length (`isCutShort`) | `Unreachable` | `Unreachable` |
| `RefusedAddressException` (a hop into the local network) | `Missing` | `Missing` |
| `CancellationException` of this coroutine | rethrown | rethrown |
| other `CancellationException`, any other `Throwable` | `Unreachable` | `Unreachable` |
| success | `Image(bytes)` | `File(name = response.fileName, bytes)` |

Checked against both files at `05f341cc5`. The cover asks its content type before its Content-Length, the picture has
only the latter; both come to `Missing` for a cover, so the order inside the shared function is free. A body that is
empty though a length was declared is `Missing` in both (the empty check comes before `isCutShort`); a cover remembers
that for good. Keep it in this refactor: an engine whose connection drops before the first body byte throws (the JVM
engines' EOF, the browser's `TypeError`), which is already `Unreachable`.

The streamed `download()` (any host, no `PublicHostsOnly`, `withStallLimit`) answers its headers through the same
`refusalOf` and its failures through the same `failureOf`, so the status rule exists twice in `importDownload/` as well.
`DropboxTransport`, `SyncHttpTransport`, and `MusicBrainzRemoteSource` have transient-status rules of their own, with
`Retry-After` and backoff; they are not this rule and stay out of the plan.

## Fix

1. `network/BoundedDownload.kt`: one function and a sealed result naming no app type (the `network` package is the
   provider core, which `check_sync_seams.py` keeps free of the module's feature packages and root package):

   ```kotlin
   internal sealed interface BoundedDownload {
       /** [contentDisposition] and [finalUrl] (the address the redirects ended at) are read before the response closes. */
       class Body(val bytes: ByteArray, val contentDisposition: String?, val finalUrl: String) : BoundedDownload
       data object Missing : BoundedDownload
       data object TooLarge : BoundedDownload
       data object Unreachable : BoundedDownload
   }

   internal suspend fun HttpClient.boundedPublicGet(url: String, maxSizeBytes: Long, timeoutMillis: Long, imagesOnly: Boolean): BoundedDownload

   /** Why [this] is not the file, decided before its body, or null where its body is to be read. */
   internal fun HttpResponse.refusal(maxSizeBytes: Long): BoundedDownload?

   /** What a download that threw [throwable] comes to; only a cancellation of this coroutine is thrown on. */
   internal suspend fun boundedFailureOf(throwable: Throwable): BoundedDownload
   ```

   Everything in the table's shared rows goes in it, `isTransient` once (private to the file). `imagesOnly` is the
   cover's content-type check, asked after `refusal` and before the body (false content type: `Missing`); a Boolean
   rather than a predicate, since there is one check. The response cannot leave `execute`, so the picture's name
   inputs come out as two strings and `importDownloadFileName` stays in `importDownload/`. No default parameters.
2. `CoverArtRemoteSourceImpl`: `client.boundedPublicGet(url, MAXIMUM_SIZE_BYTES, DOWNLOAD_TIMEOUT_MILLIS, accept = { it.contentType()?.match(ContentType.Image.Any) == true })`
   mapped `Body → Image`, `Missing → Missing`, **`TooLarge → Missing`**, `Unreachable → Unreachable`.
3. `ImportDownloadRemoteSourceImpl.downloadPicture`: the same with `PICTURE_TIMEOUT_MILLIS` and `imagesOnly = false`,
   `Body` mapped to `File(name = importDownloadFileName(contentDisposition, finalUrl), bytes)`, the rest one to one
   through one private `BoundedDownload.toImportDownload()`. `download()` (the streamed archive) keeps its request, its
   stall limit, and `streamBody`, and replaces its private `refusalOf` and `failureOf` with
   `response.refusal(maxSizeBytes)?.toImportDownload()` and `boundedFailureOf(throwable).toImportDownload()` (its
   `SinkFailure` catch stays first). The `fetch` helper, `refusalOf`, `failureOf`, and both `isTransient` copies go;
   `HttpResponse.fileName` stays for `streamBody`'s `name`.
4. § Downloads in `remote/implementation/CLAUDE.md`: name `network/BoundedDownload` as where the shared rule lives.
   Keep the per-caller sizes and the `Missing`/`Unreachable` split in the `coverArt/` and `importDownload/` KDoc as now.

## Tests

`CoverArtRemoteSourceTest` and `PictureDownloadTest` pin the rows above through a mock engine. Before the refactor,
check each row of the table has a case in each test; add the missing ones first (likely: a cover whose
Content-Length is past the ceiling is `Missing`, not anything else; a picture whose body is empty is `Missing`; a
picture whose transport throws is `Unreachable`; a picture redirected to another name is named by the final address),
so the refactor is checked against today's behaviour. `ImportDownloadRemoteSourceTest` and `StreamedImportDownloadTest`
pin the streamed path's refusals (transient, other non-2xx, too large declared) and failures; check they do, then all
four pass unchanged. A `BoundedDownloadTest` is only needed for a row no caller can reach.
