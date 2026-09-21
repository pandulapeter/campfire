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

Implements `:data:source:remote:api`. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object
DataRemoteSourceModule`, whose two `@Single` functions build what is not simply constructed — the HTTP client and the
list of providers — while the stores and the four platform authenticators (`AndroidSyncAuthenticator`, …, each a
`@Single` in its own source set) declare themselves. The only module in the project that makes a network call, and
the only one that sees Ktor.

Providers are registered as one `SyncProviders` holding the list (never as a bare `List<SyncProvider>`, which the Koin
compiler plugin injects as `getAll<SyncProvider>()` and so as an empty list), and **a provider the build has no credentials for is left out of
that list entirely** rather than offered and then failing — the settings screen shows what is in it, so a build
without a key says so instead of inviting the user to press a button that cannot work. The key goes in
`local.properties` as `campfire.dropbox.appKey` (see the Build section of the root `CLAUDE.md`).

Getting one means registering a *scoped access* app with the **App folder** permission at
https://www.dropbox.com/developers/apps, ticking `files.content.read`, `files.content.write` and `account_info.read`
under Permissions, and adding a redirect URI per platform under Settings → OAuth 2 (`campfire://oauth` for Android
and iOS, `http://127.0.0.1:53682` for the desktop, and the exact page URL for a web deployment). Dropbox matches
redirect URIs character for character, which is why the desktop port is fixed.

- `dropbox/DropboxSyncProvider` — the app is registered for the *app folder* permission, so every path is inside
  `Apps/Campfire` and the rest of the user's Dropbox is invisible to it. Uploads use Dropbox's `update` write mode
  with the expected revision, so a write that would clobber another device's change is refused by the service rather
  than prevented by hope; `autorename` is off, because a name Dropbox invented would be a song nobody asked for.
  Deletes carry `parent_rev` for the same reason, and "already gone" counts as success.
- Every call is retried while Dropbox answers 429, 5xx, or a 409 whose summary says `too_many_write_operations` (its
  answer to several writes landing in one folder at once, which the engine's own concurrency provokes), waiting what
  the body's `retry_after` or the `Retry-After` header asks for — or, where neither says, a wait that doubles from 2 s
  to a ceiling of 32 s over six attempts, so that an outage of a minute or so is sat out — plus a little jitter
  (several transfers are in flight at once and would otherwise all come back together and be limited again). A first
  sync of a whole library *will* be rate limited; treating that as a failure would mean a library that can never
  finish its first sync.
- A 409 whose summary says `insufficient_space` is a full account and becomes `SyncRemoteStorageFullException`,
  which ends the run; any other 409 is the refusal of that one file.
- `dropbox/DropboxModels` — the parts of the API's answers that are read. Everything defaulted, unknown keys
  ignored: a field added on the other side must never turn into a parse failure the user sees as a broken sync.
- OAuth is **PKCE with no client secret**, which is what lets this work with no server of Campfire's own. Tokens are
  renewed inside a single `SyncCredentialsStore.update`, so the several requests of one run cannot each start a
  refresh and have the last one to finish overwrite the tokens the others are using. Dropbox may hand out a new
  refresh token on renewal, and dropping it would end the connection silently. Whether a token is still good is
  judged by the device's clock, which can be wrong, so a 401 is answered once with a forced refresh and the request
  sent again before it is reported as a refusal that needs the account connected again.
- `auth/SyncAuthenticator.<platform>.kt` — the four ways back from a consent page. **Noticing that the user backed
  out is as much a part of each of these as the redirect itself**: a consent page lives somewhere the app cannot
  see, so an authorization that only ever waits for a redirect leaves the UI on "waiting for the browser" forever
  with no way back.
  - **iOS** uses `ASWebAuthenticationSession`, which is the only iOS API that reports a dismissal: its completion
    handler is given a null URL and an error. It also keeps the sheet inside the app and shares Safari's cookies.
    The redirect never reaches `onOpenURL`, so nothing forwards it.
  - **Android** opens the user's own browser (never a WebView: a page asking for a password has to be somewhere the
    address bar is visible) and receives `campfire://oauth` as an intent, which the launcher activity forwards
    through the public `onSyncRedirectReceived`. Nothing reports a dismissed browser, so what is watched instead is
    the app itself coming forward again through `ActivityLifecycleCallbacks`; the redirect intent brings the
    activity forward too, so it is given a short grace period to arrive before the attempt counts as abandoned.
  - **Desktop** becomes a web server for the length of one authorization — a socket on `127.0.0.1:53682` whose
    address is the redirect URI. The port is fixed because a service only redirects to a URI registered with it
    character for character. `accept` blocks a thread and notices neither a cancelled coroutine nor an interrupt, so
    cancelling closes the socket underneath it from a sibling coroutine; `DesktopSyncAuthenticatorTest` covers that,
    since getting it wrong holds the port for five minutes and is invisible until somebody cancels. Browsers open a
    speculative second connection next to the navigation and ask for a favicon after it, so the listener reads each
    connection with a timeout of its own, answers what is not the redirect with a 404, and keeps listening until the
    redirect arrives.
  - **Web** navigates away and reads the answer out of the query string at the next start, taking it out of the
    address bar as it does so, so a reload cannot replay a spent code. Cancelling on the service's page comes back
    as `error=access_denied`, which is handled like any other refusal.
- `crypto/` — `Pkce` (verifier, S256 challenge, state) and `dropboxContentHash` (SHA-256 of each 4 MB block,
  concatenated, hashed again), both on the `Sha256` in `:data:source:remote:api`.
- `network/` — the `HttpClient` factory, one engine per target (OkHttp, CIO, Darwin, `fetch`), and `Json.kt`:
  `toAsciiJsonString` exists because Dropbox takes upload and download arguments in an HTTP header, which may only
  carry ASCII, and Campfire's files are named after song titles.

Tested in `commonTest`, run on the desktop target: the hashing, the encoders, and the authorization URL — get a
parameter wrong there and the user meets an error page on the service's own site with nothing in the app to say why —
and, against a Ktor `MockEngine` in virtual time, how requests answer being told to slow down, and being cancelled or
timing out.
`desktopTest` adds the one platform piece worth testing, the loopback server's cancellation.
