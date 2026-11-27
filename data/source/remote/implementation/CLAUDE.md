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

**The only module that makes a network call and the only one that sees Ktor.** Silent failures go to the injected
`Logger`.

## What reaches the network

- The list is the root `CLAUDE.md`'s; add to it before adding a request. Coil has no network artifact.
- `User-Agent` (`USER_AGENT`): nothing about the user (MusicBrainz requires one). None in the browser: a script cannot set it, and it would turn a plain GET into a CORS
  preflight.
- What a platform refuses is just not shown: the web reaches only CORS-enabled hosts; plain `http://` is refused on
  Android and the web (iOS allows it to IP literals; covers ask `https` only).

## Provider core and configuration

- **`auth/`, `network/`, and `crypto/` are the provider core**, for any provider and any app: they import nothing of
  `dropbox/`, the catalogues, or the module root, which `.github/scripts/check_sync_seams.py` checks (main sources
  only). A provider is a package beside `dropbox/` built from its own configuration; Campfire's values are the root's:
  `CAMPFIRE_OAUTH_REDIRECT` (`OAuthRedirectConfiguration`: scheme, host, desktop port; the shells' `isSyncRedirect`
  read it directly, since an iOS cold start asks before the Koin graph exists; the manifest and `Info.plist` repeat it),
  `USER_AGENT`, and the generated `RemoteConfiguration.kt` (`DROPBOX_APP_KEY`, `CAMPFIRE_VERSION`).
- **A provider without credentials in the build is left out of `SyncProviders`**, so Settings says sync is not
  configured instead of offering a dead button. `DataRemoteSourceModule.syncProviders` hands Dropbox a
  `DropboxConfiguration`: `campfire.dropbox.appKey`, the collections, and the folder document cap.
- Dropbox app setup: *scoped access*, **App folder**; scopes files.content.read, files.content.write, account_info.read,
  sharing.read, and sharing.write. The authorization URL names no scopes, so a token granted before a scope was ticked
  lacks it (`missing_scope`, below). Redirect URIs `campfire://oauth` (Android, iOS), `campfire-debug://oauth` (Android's debug build, whose
  shell hands its scheme in as `SyncRedirectScheme`), `http://127.0.0.1:53682`
  (desktop), and the exact page URL per web deployment, matched character for character, hence the fixed port.
- `MusicBrainzRemoteSource` and `ITunesRemoteSource` are built once each and shared by the cover search and details
  lists, so both share one `MusicBrainzRateLimiter`.

## Dropbox (`dropbox/`)

- Every path is inside `Apps/Campfire Sync`: `/<collection id>/<name>`, folder documents at `/<name>`, never listed. A
  document's `path/not_found` is no document.
- Uploads use `add` for a new file and `update` with the expected revision, so a clobber is refused (`path/conflict` →
  `Conflict`); `autorename` off. An `update` over a file deleted since writes it again, its folder too (measured); only a
  file written or re-added since conflicts. Deletes carry `parent_rev`; "already gone" is success, and a file changed
  since is left for the next run.
- A download must match the `size` and `content_hash` its header names, or that file fails; one past its limit (the
  collection's, or the folder document cap) is refused before the body, not retried (`DropboxDownloads`). An
  undecodable answer (a 200 HTML page from something in between) is `SyncNetworkException`.
- Change feed (`DropboxChangeFeed`): a top-level deletion is reported as a document, since a collection's folder may go
  as one entry; Dropbox holds a longpoll ~104 s past the asked time (measured).
- `ConnectivityMonitor` (`network/`): offline-to-online changes only, never the starting state, no permission prompt;
  the desktop has none, the JVM having no such callback.
- Restoring (`DropboxDeletedFiles`): any refusal, a missing scope too (logged once), is "not restored".
- A run's deletions go as `files/delete_batch` jobs: single deletions in parallel hit the folder write lock and crawl at
  ~1/s. A job runs at ~4–7 files/s and is not atomic: a listing meanwhile is a consistent snapshot of any fraction of it
  (measured on 300 files). A listing right after another client's upload, rename, or deletion has always shown it.
- Errors (`DropboxErrors`): 409 `too_many_write_operations` is retried, any other 409 but `insufficient_space` fails
  that one file. Every call is safe to resend but the longpoll, sent once; the watcher decides what its failure means.
- `DropboxModels`: the fields a decision rests on are required, and an answer without them is the network failing.

### Shared copies (`DropboxSharing`)

- `/shared/<shareId>.zip`, uploaded like library files; `toSharedFiles` is called by `list` only, never through
  `toRemoteFiles`, which the change feed shares.
- Linked with `create_shared_link_with_settings` (public, viewer). Its `shared_link_already_exists` carries **no**
  metadata when settings are sent (measured), so the link is read with `list_shared_links`. Only the `/scl/fi/` form is
  read; any other (an old `/s/` link) is `Refused`, since no download could be built. Whether a copy is there is
  `files/get_metadata`'s answer, never a listing's, since a share marked dead stays dead.
- Opening a link needs only `fileId`, `rlkey`, and some name, from `dl.dropboxusercontent.com` (measured,
  undocumented): the zip without a redirect, with `Access-Control-Allow-Origin: *`, a dead link 403/404.
  `www.dropbox.com` answers every dead link 200 with HTML, and its redirect has no CORS header.

## Sync calls (`network/SyncHttpTransport.kt`)

Shared by every provider; service specifics come from its `SyncHttpErrorMapper`.

- Inactivity is bounded, never the whole call (a listing page or a large file on 2G takes minutes while data arrives),
  except in the browser, whose `fetch` has no inactivity timeout (`HttpClientConfiguration`).
- A first sync *will* be rate limited; it must not fail for it: 408, 429, 5xx, and what the mapper says asks to slow
  down are waited out and retried, with the counts and waits in `SyncHttpTransport`; a requested wait over 120 s ends
  the run as `SyncNetworkException`.
- The browser reports a failed `fetch` as a `kotlin.Error`, so any non-cancellation `Throwable` is unreachable; so is an
  undecodable answer (`decodeSyncAnswer`).

## OAuth

- PKCE, no server of the app's own; Dropbox takes no client secret (`OAuthTokens` sends one only when configured).
- Every provider's rules, in `OAuthTokens` and, for the 401, `SyncHttpTransport`:
  - Tokens renew inside one `SyncCredentialsStore.update`, one refresh shared by concurrent requests (bounded at 30 s,
    retried like any call when unreachable). Keep a new refresh token the service hands out, or the connection ends
    silently. The store caches only what it saw stored: a refused or cancelled write drops the cache; a refused read
    (`LibraryStorageException`) is thrown, not cached, so a document without tokens never overwrites unreadable ones.
  - Only a 400 or 401 from the token endpoint is a refusal; any other answer (a proxy's 403 or 407, a portal's redirect)
    is the network.
  - A 401 gets one forced refresh (of the refused token only, so simultaneous refusals share it) and a resend before it
    is a refusal; the device clock may be wrong.
- A 401 whose `error_summary` starts with `missing_scope` is `SyncScopeMissingException` at once, without a refresh: a
  renewed token has the same scopes, and a refusal would show the working connection as failed.
- Each platform must notice the user backing out as well as the redirect, or the UI waits forever. `Cancelled` with no
  message means the user backed out.
  - **iOS**: `ASWebAuthenticationSession`, the only API that reports dismissal (`CanceledLogin` → `Cancelled`). The
    redirect never reaches `onOpenURL`.
  - **Android**: the user's own browser, never a WebView (the address bar must be visible). The redirect arrives through
    `onSyncRedirectReceived`; dismissal is inferred from the app returning to the front after a short grace period.
    After process death the redirect waits for `consumePendingRedirect`.
  - **Desktop**: a loopback server on the configured port for one authorization; if the browser never opens, the
    attempt ends at once. `accept` ignores cancellation and interrupts, so cancelling closes the socket from a sibling
    coroutine (`LoopbackRedirectListenerTest`; getting it wrong holds the port for five minutes).
  - **Web**: navigates away (`Redirected`), the PKCE verifier written to OPFS first; the answer is read from the query
    string at the next start and removed from the address bar so a reload cannot replay the code.
    `error=access_denied` is a refusal. The redirect URI is the `<base>` folder (not the address bar), ending in `/`,
    without `index.html`; registered: `https://campfire-songbook.com/app/` (not the `github.io` address that redirects
    to it) and `http://localhost:8080/`.
- Revocation renews from the captured refresh token if needed (never stored), then revokes, each bounded at 10 s.

## Downloads

Each answer's sizes, bounds, and `Missing` / `Unreachable` split are in `coverArt/` and `importDownload/`. What they
share lives once, in `network/BoundedDownload` (the streamed import takes its status and failure rules too): a cover's
or a picture's every redirect hop goes only to a public `https` host (`isPublicHttpsAddress`); nothing
is read past its ceiling; 408, 429, 5xx, a transport failure, refused CORS, or a short body may change soon
(`Unreachable`), any other refusal will not (`Missing`); no content type is trusted for a picture, which the domain
tells by its bytes. A streamed import has no total bound, only a stall limit; the rest are bounded so a stalled host
cannot hold the repository's download slots. On the web a host without CORS headers is `Unreachable`, which the UI
cannot tell from no answer.

## Catalogues

- Any transport `Throwable` → `CatalogueSearchException`. Queries, ranking, and artwork addresses are in
  `musicBrainz/` and `iTunes/`. MusicBrainz refuses everything while a client averages over 1/s, hence one
  `MusicBrainzRateLimiter` per app; a `Retry-After` over 32 s fails the search at once. iTunes allows ~20
  requests/minute and gets no pace of its own.
- Candidates that would fill in the same values are merged (duration to the second).

## Pages (`page/`)

- `OffscreenPageSource` is the common loop over a platform's `OffscreenBrowser`: a look (`PAGE_SNAPSHOT_SCRIPT`:
  `innerText` with `content-visibility` forced visible for the read, since a page never scrolled would leave out what it
  skips, and a chord drawn above its syllable written as a chord row over its line, not glued into the word (inline) or
  left a chunk a line (stacked over a block or flex item of its own) (`PageSnapshotScript.kt`); title, address; per line, the structure marks the page model can read, found by aligning
  the visible text nodes with `innerText`, decoded only for the look taken into `LoadedPage.Read.marks`, none where any
  is malformed) every 500 ms, taken once loaded and its text, title, and address unchanged for 1.5 s (a style changing
  alone changes only the marks), else the last readable look at 20 s;
  a bot check (`PageSnapshot.isBotCheck`, short pages only) is waited past, never taken. The view is let go of
  however the read ends. Nothing read is `UNREACHABLE` where the load failed for the network (`PageLook.isUnreachable`)
  or the page was still loading with nothing readable at 20 s, which trying again may get past; else `UNREADABLE` (a
  bot check, an error page, a page loaded with no text). An `http` address is read by its `https` form.
- Android: a `WebView` attached to no window, laid out at the screen's size, scripts on, no file or content access,
  pictures not loaded, third-party cookies refused; navigations and every subresource (a service worker's too, through
  the process-wide `ServiceWorkerClient` set with the first view) refused off a public `https` host (`data:` and
  `blob:` aside); its cache, cookies, and storage emptied once it is let go of. A 404 or 410 fails the load; a 403 or
  503 may be a bot check that clears. A failed host lookup (also what no connection reports), connection, timeout, or
  I/O error is the network's.
- iOS: a `WKWebView` in no window with a non-persistent data store; every navigation (frames too, which may also be
  `about:`, `data:`, `blob:`) refused off a public `https` host. The macOS helper's content rule list (compiled once per
  process into Caches, the views going without it where it fails) refuses pictures, media, `http:`, and `ws:`; the
  page's other subresources are its own, as in Safari. An `NSURLErrorDomain` failure of no connection, a host not found,
  not answering, or lost, or a timeout is the network's.
- Web: `NoPageSource` (a tab may not read another site's page).

### Desktop (`NativeWebView`, `src/desktopMain/native/`)

- No browser engine is bundled: a JNI helper of the app's own (`campfirewebview`, 50–60 KB on macOS) drives the system's
  web view, loaded from the app resources folder (`compose.application.resources.dir`); `:app:desktop` compiles it for
  the host (`CompileWebViewHelper`). Not FFM (the runtime is Java 21) and not JNA, JCEF, or JavaFX. Without the helper,
  or with no engine (`isEngineAvailable`: no WebView2 runtime; no WebKitGTK or no display), it is `NoPageSource`.
- Every native call returns at once and the engine answers on its own thread through `NativeWebViewEvents`, whose
  method names are the C side's contract (`proguard-rules.pro` keeps them); the helper hands an error's domain and
  code over raw and `NativeLoadFailure` reads them (tested). Strings cross as UTF-16, since `NewStringUTF`'s modified
  UTF-8 mangles a page's emoji. Laid out at 1280 × 800.
- **macOS**: a `WKWebView` in no window, non-persistent data store, built and touched on the AppKit main thread (AWT's
  first thread, not Compose's event thread) by `dispatch_async`, never waited for, so nothing deadlocks with AWT. A
  content rule list (compiled once into a store in the temporary folder) refuses pictures, media, `http:`, and `ws:`;
  navigations as on iOS. Works under the Mac App Store sandbox (`network.client`; tried with the store entitlements).
- **Windows**: WebView2, InPrivate, in a never-shown window, on a thread of the helper's own (an STA with a message-only
  window and its loop) for the life of the process; the loader linked in statically from the pinned SDK (NuGet, checked
  by SHA-256; credited). Every request, frames' and workers' included, goes through `WebResourceRequested`: pictures
  and media and anything off a public `https` host get a 403, as on Android. New windows, downloads, permissions, and
  script dialogs are refused. Its browser processes' folder is `%TEMP%\Campfire WebView2`.
- **Linux**: WebKitGTK through `dlopen` (`webkit2gtk-4.1`, else 4.0; the few functions declared by hand; the `.deb`
  recommends it rather than depending on it, the AppImage cannot name it), an ephemeral
  context, pictures off, in a `GtkOffscreenWindow`, on a GTK thread of the helper's own. It iterates the default main
  context only while a view is open, since AWT's GTK file dialog runs a loop over the same context on another thread.
  WebKitGTK does not tell a frame's navigation from the page's, so each is asked as a frame's (about:, data:, blob:
  allowed, and an address off a public host never read). Subresources other than pictures are the page's. Downloads are
  refused: a response that is an attachment or that the view cannot show is ignored, and any download started is
  cancelled.
