<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:source:remote:implementation

Implements `:data:source:remote:api`. **The only module that makes a network call and the only one that sees Ktor.**
Silent failures go to the injected `Logger`.

## What reaches the network, and when

- The list is the root `CLAUDE.md`'s; add to it before adding a request. Cover copies are kept by
  `:data:repository:implementation`.
- The import download (`ImportDownloadRemoteSourceImpl`), one GET of an address the user opened, keeping nothing;
  reached by nothing in the app yet.
- Coil has no network artifact: it draws what `GetCoverArtUseCase` hands it.
- `HttpClientHolder` builds the one client lazily on the first request, on `Dispatchers.Default`, so a user who never
  syncs or searches never builds it.
- `User-Agent` (`UserAgent.kt`): `Campfire/<campfire.versionName> ( https://github.com/pandulapeter/campfire )`, naming
  the app and nothing about the user (MusicBrainz requires it). Not set in the browser: a script cannot, and an
  unexpected header would turn a plain GET into a CORS preflight.
- What a platform refuses is just not shown: the web reaches only CORS-enabled hosts, and plain `http://` is refused on
  Android and the web (iOS allows it to IP literals; covers ask `https` only).

## Configuration

- `SyncProviders` wraps the provider list (never a bare `List`, see root `CLAUDE.md`). **A provider without credentials
  in the build is left out entirely**, so Settings says sync is not configured instead of offering a dead button.
- The key is `campfire.dropbox.appKey` → `DROPBOX_APP_KEY` in the generated `RemoteConfiguration.kt` (with
  `CAMPFIRE_VERSION` for the `User-Agent`).
- Dropbox app setup: a *scoped access* app with the **App folder** permission; scopes files.content.read,
  files.content.write, account_info.read, sharing.read, and sharing.write (song links). The authorization URL
  names no scopes, so a token granted before a scope was ticked lacks it (`missing_scope`, below); redirect URIs
  `campfire://oauth` (Android, iOS), `http://127.0.0.1:53682` (desktop), and the exact page URL per web deployment.
  Dropbox matches them character for character, hence the fixed desktop port.
- `MusicBrainzRemoteSource` and `ITunesRemoteSource` are each built once in `DataRemoteSourceModule` and handed to both
  the cover search and song details lists (MusicBrainz first), so both share one `MusicBrainzRateLimiter`.

## Dropbox (`dropbox/`)

- App-folder scope: every path is inside `Apps/Campfire Sync`. Library files are `/songs/<name>` and
  `/setlists/<name>`; folder documents (`preferences.json`) are `/<name>`, never listed. A document's `path/not_found`
  is no document.
- Uploads use `add` mode for a new file and `update` with the expected revision otherwise, so the service refuses a
  clobber (`path/conflict` → `Conflict`); `autorename` is off. Deletes carry `parent_rev`; "already gone" is success,
  and a file changed since is left for the next run to bring back.
  A download's revision comes from the `Dropbox-API-Result` header (its absence is a failure), and its `size` and
  `content_hash`, which the bytes must match, or that file fails (not the run). A download is streamed
  (`DropboxDownloads`) and refused before its body is read once its declared size, or the body itself, passes 8 MiB:
  a library file then fails as one file, a folder document is `SyncDocumentTooLargeException` with its revision;
  neither is retried or a network failure. An answer that cannot be decoded is `SyncNetworkException`: a 200 HTML page
  comes from something in between, not from Dropbox.
- Watching (`DropboxChangeFeed`): `list_folder/longpoll` on `notify.dropboxapi.com` takes the cursor of a listing's
  last page and no token, held 240 s (under the ~5 minutes mobile carriers keep a silent connection) plus Dropbox's
  documented 90 s (104 s measured), with the call's own bounds raised past that; its `backoff` delays the next poll
  (a quiet answer waits it before returning, a change is answered at once), and a `reset` cursor answers "changed". `list_folder/continue` from the cursor gives the changes; a top-level deletion is reported as a
  document, since a library folder deleted whole may come as one entry.
- `ConnectivityMonitor` (per platform, in `network/`): offline-to-online changes only, never the starting state, and
  nothing that needs a permission prompt. Android: the default network becoming validated (`ACCESS_NETWORK_STATE`,
  install-time; a network whose validation check is blocked never is, so its waits run their full length); iOS: the
  Network framework's path monitor; web: the `online` event; desktop: nothing, the JVM has no such callback.
- A run's deletions go as `files/delete_batch` jobs (≤1000 files) polled with `delete_batch/check`: single deletions in
  parallel hit the folder write lock (`too_many_write_operations`) and crawled at ~1/s. A job runs at ~7 files/s and
  is not atomic. Entries refused as busy are retried with the normal back-off.
- Timeouts: connect 20 s, inactivity 60 s, no total bound (a large `list_folder` page or a song on 2G takes minutes
  while data arrives). The browser has a 10-minute total, since `fetch` has no inactivity timeout; Dropbox calls carry
  a shorter one of their own there (60 s RPC, 120 s listing pages and downloads, uploads by size).
- Retries on 408, 429, 5xx, and 409 `too_many_write_operations`, waiting `retry_after` / `Retry-After`, else 2 s
  doubling to 32 s, six retries, plus up to 0.5 s jitter; a requested wait over 120 s ends the run as
  `SyncNetworkException` instead. Transport failures are retried too, counted apart from the waits: timeouts six times,
  anything else (a reset, a cut stream, or a failed `fetch`) three, since every call here is safe to resend; the
  longpoll alone is sent once, and the watcher decides what its failure means. A first sync *will* be rate limited; it
  must not fail for it.
- 409 `insufficient_space` → `SyncRemoteStorageFullException` (ends the run); any other 409 fails that one file.
- Shared copies (`DropboxSharing`, `DropboxSharedLinkAddress.kt`): `/shared/<shareId>.zip`, uploaded like library
  files. `toSharedFiles` is called by `list` only, never through `toRemoteFiles`, which the change feed shares.
  - The link is `create_shared_link_with_settings` (public, viewer). Its `shared_link_already_exists` carries **no**
    metadata when settings are sent (measured), so the existing link is read with `list_shared_links`
    (`direct_only`).
  - Visibility is `resolved_visibility`, else `effective_audience`; anything but `public` is `NotPublic`. Any other
    409 is `Refused`.
  - The address `https://www.dropbox.com/scl/fi/<fileId>/<name>?rlkey=<rlkey>&dl=0` is cut into `fileId` and `rlkey`
    (each `[A-Za-z0-9_-]{1,64}`); any other form (an old `/s/` link) is `Refused`, since no download could be built.
  - Deleted with `files/delete_v2`; `path_lookup/not_found` is success. A deleted file's link never comes back.
  - Opening a link (`SharedLink.Readable.downloadUrl`) needs only `fileId`, `rlkey`, and some name, from
    `dl.dropboxusercontent.com` (measured, undocumented): it serves the zip without a redirect, with
    `Access-Control-Allow-Origin: *`, so the web downloads like the rest, and answers a dead link 403/404. The
    `www.dropbox.com` address answers every dead link 200 with HTML, and its redirect has no CORS header.
- `DropboxTransport` (retries, 401 renewal, failure mapping) and `DropboxTokens` are built by the provider, not Koin.
- `DropboxModels`: unknown keys ignored, optional fields defaulted, so a new API field never breaks sync; the fields a
  decision rests on (listing entries, cursor, `has_more`; revisions; the access token; the delete job's tag) are
  required, and an answer without them is the network failing.
- `toAsciiJsonString`: Dropbox takes arguments in an HTTP header, which is ASCII only, and file names are song titles.
- The browser engine reports a failed `fetch` as a `kotlin.Error`, so `transport` treats any non-cancellation
  `Throwable` as unreachable.

## OAuth

- PKCE, no client secret, no server of Campfire's own.
- Tokens renew inside one `SyncCredentialsStore.update`, so concurrent requests share one refresh, bounded at 30 s,
  and retried like any call when it cannot reach Dropbox. A renewal that could not reach Dropbox is shared too: the
  callers that waited for it fail with it rather than each trying their own. Keep a new refresh token Dropbox hands out, or the connection
  ends silently. The store caches only what it saw stored: a refused or cancelled write drops the cache; a refused read
  (`LibraryStorageException`) is thrown and not cached, so a document without tokens never overwrites tokens
  unreadable for a moment.
- Only a 400 or 401 from the token endpoint is a refusal; any other answer (a proxy's 403 or 407, a portal's redirect)
  is the network.
- A 401 gets one forced refresh (of the refused token only, so simultaneous refusals share it) and a resend before it
  is reported as a refusal; the device clock may be wrong.
- A 401 whose `error_summary` starts with `missing_scope` (`error.required_scope` names it) is thrown at once as
  `SyncScopeMissingException`, without a refresh: a renewed token has the same scopes, and reporting it as a refusal
  would end the run and show the working connection as failed (`DropboxTransportTest`).
- Each platform must notice the user backing out as well as the redirect, or the UI waits for the browser forever.
  `Cancelled` with no message means the user backed out.
  - **iOS**: `ASWebAuthenticationSession`, the only API that reports dismissal (`CanceledLogin` → `Cancelled`; other
    errors keep their description). The redirect never reaches `onOpenURL`.
  - **Android**: the user's own browser, never a WebView (the address bar must be visible). `campfire://oauth` arrives
    as an intent forwarded through `onSyncRedirectReceived`. Dismissal is inferred from the app returning to the front
    (`ActivityLifecycleCallbacks`) after a short grace period for the redirect. After process death the redirect waits
    for `consumePendingRedirect`.
  - **Desktop**: a server on `127.0.0.1:53682` for one authorization. The browser opens through `SystemBrowser` (the
    shell's URL opener); if it never opens, the attempt ends at once. `accept` ignores cancellation and interrupts, so
    cancelling closes the socket from a sibling coroutine (`DesktopSyncAuthenticatorTest`; getting it wrong holds the
    port for five minutes). Each connection is read with a timeout and non-redirect requests (speculative connections,
    favicon) get a 404 while listening continues. An attempt nobody answers ends after five minutes.
  - **Web**: navigates away (`Redirected`), the PKCE verifier written to OPFS first; the answer is read from the query
    string at the next start and removed from the address bar so a reload cannot replay the code.
    `error=access_denied` is a refusal. The redirect URI is the `<base>` folder (not the address bar), ending in `/`,
    without `index.html`; registered: `https://campfire-songbook.com/app/` (the custom domain, not the `github.io`
    address that redirects to it) and `http://localhost:8080/`.
- `disconnect` forgets the local credentials at once and answers the revocation for the caller to run in the
  background: a renewal from the captured refresh token if needed (never stored) and the revoke, each bounded at 10 s.

## Downloads

- Cover (`coverArt/`): redirects followed; only public `https` hosts, checked on every redirect hop
  (`isPublicHttpsAddress`, `PublicHostsOnly`); a refused hop is `Missing`. Not `image/*` or over 5 MB (read one byte
  past the limit when no length is declared) → `Missing`, never read to the end. 408, 429, 5xx, transport failure, or a
  body shorter than an unencoded declared length → `Unreachable`; any other refusal → `Missing`. At most a minute per
  download, so a stalled host cannot hold the repository's download slots.
- Import (`importDownload/`): declared length over the ceiling → `TooLarge` before the body; otherwise read one byte
  past it. 408, 429, 5xx, transport failure, refused CORS, or a body shorter than an unencoded declared length →
  `Unreachable`; other refusals or an empty body → `Missing`. Content type is not checked.

## Catalogues

- `network/CatalogueTransport.kt`: any transport `Throwable` → `CatalogueSearchException`; answers decoded on
  `Dispatchers.Default` (a recording search is large).
- **MusicBrainz** (`musicBrainz/`, pure part `MusicBrainzSearch`): values are Lucene phrases (escape only `"` and `\`).
  - Cover search: release groups (by album, else through the title's recordings), narrowed by artist; each address is
    the Cover Art Archive's `front-250`, so a missing cover shows as the thumbnail's 404.
  - Details lookup (`recordingQuery`: title, artist, album): keep scores ≥ 70; within a score, studio albums (`Album`, no
    secondary type) first, then singles and EPs, since live bootlegs and compilations also score 100. The album filled
    in is the earliest studio album, else earliest EP, else none.
  - Songwriters: one recording lookup with its work relations (`MusicBrainzSearch`, tested).
  - Pace: `MusicBrainzRateLimiter`, one per app, 1.1 s between request starts (MusicBrainz refuses everything while a
    client averages over 1/s). 503 / 429 wait for `Retry-After`, else 2 s doubling to 32 s, at most five retries,
    calling `onBusy` before each; a `Retry-After` over 32 s fails the search at once.
- **iTunes** (`iTunes/`, pure part `ITunesSearch`): no key, CORS headers on search and artwork.
  - Cover search: searches *songs* with artist plus album (or title) as one term (the album search matches the album
    name only); candidates are their albums. Artwork is the 100 px address rewritten to `250x250bb.jpg`; any other form
    is left out, not guessed.
  - Details lookup: songs by artist and title; album is the collection unless ` - Single`. No songwriters:
    `loadSongCredits` answers null without a request.
  - ~20 requests/minute, so no pace of its own; a refusal is a failure.
- Candidates that would fill in the same values are merged (`songDetails/SongDetailsCandidates.kt`, duration to the
  second).

## Other

`crypto/`: `Pkce` and `dropboxContentHash` (SHA-256 per 4 MB block, concatenated, hashed again), on `Sha256` from
`:data:source:remote:api`. The authorization URL is tested because a wrong parameter shows an error page on the
service's site with nothing in the app to explain it.
