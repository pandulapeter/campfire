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
DataRemoteSourceModule`, whose three `@Single` functions build what is not simply constructed — the HTTP client, the
list of providers and the list of cover searches (MusicBrainz first, then iTunes), the first built with the clock its
tests replace — while the stores and the four platform authenticators (`AndroidSyncAuthenticator`, …, each a
`@Single` in its own source set) declare themselves. The only module in the project that makes a network call, and
the only one that sees Ktor.

Providers are registered as one `SyncProviders` holding the list (never as a bare `List<SyncProvider>`, which the Koin
compiler plugin injects as `getAll<SyncProvider>()` and so as an empty list), and **a provider the build has no credentials for is left out of
that list entirely** rather than offered and then failing — the settings screen shows what is in it, so a build
without a key says so instead of inviting the user to press a button that cannot work. The key goes in
`local.properties` as `campfire.dropbox.appKey` (see the Build section of the root `CLAUDE.md`). It reaches the code as
`DROPBOX_APP_KEY` in the `RemoteConfiguration.kt` that `generateRemoteConfiguration` writes, next to
`CAMPFIRE_VERSION`, taken from `campfire.versionName` for the `User-Agent`.

Getting one means registering a *scoped access* app with the **App folder** permission at
https://www.dropbox.com/developers/apps, ticking `files.content.read`, `files.content.write` and `account_info.read`
under Permissions, and adding a redirect URI per platform under Settings → OAuth 2 (`campfire://oauth` for Android
and iOS, `http://127.0.0.1:53682` for the desktop, and the exact page URL for a web deployment). Dropbox matches
redirect URIs character for character, which is why the desktop port is fixed.

- `dropbox/DropboxSyncProvider` — the app is registered for the *app folder* permission, so every path is inside
  `Apps/Campfire Sync` (the folder is named after the app's name in the App Console) and the rest of the user's
  Dropbox is invisible to it. Uploads use Dropbox's `update` write mode
  with the expected revision, so a write that would clobber another device's change is refused by the service rather
  than prevented by hope; `autorename` is off, because a name Dropbox invented would be a song nobody asked for.
  Deletes carry `parent_rev` for the same reason, and "already gone" counts as success. A run's deletions go as
  `files/delete_batch` jobs of up to 1000 files, polled with `delete_batch/check` until they finish: Dropbox locks the
  app folder for every write, so single deletions sent side by side mostly came back `too_many_write_operations` and
  crawled through the back-off at about one a second, long enough for another device to list the folder half deleted
  and follow the part it saw without asking. A job takes the lock once and removes about seven files a second
  (measured: 314 files in 51 s). It is not atomic — the files disappear one after another while it runs — so the
  window is shorter, not gone. Entries it turns away as busy (or a whole job refused that way) are sent again after
  the same back-off as any other write. The library files are `/songs/<name>` and `/setlists/<name>`; a document of
  the folder's own (`downloadDocument` / `uploadDocument`, so far only `preferences.json`) is at the top, `/<name>`,
  which the listing never reports. The revision of any download, a library file's or a document's, comes back in its
  `Dropbox-API-Result` header (one without it is a failure), and a document's `path/not_found` is no document rather
  than a failure.
- The client bounds the time to connect (20 s) and the silence between two packets (60 s), and not a request as a
  whole: a `list_folder` page of a few thousand songs, or one long song, takes minutes on a 2G or congested link while
  data is arriving the whole time, and a total bound would end every run of such a library on such a link. The
  browser keeps a total bound of ten minutes, since `fetch` has no timeout between packets. A timeout of any of the
  three is retried with the same doubling wait as the answers below, since on a phone it is more often a cell
  handover than a service that is gone; it ends the run only once the attempts run out.
- Every call is retried while Dropbox answers 429, 5xx, or a 409 whose summary says `too_many_write_operations` (its
  answer to several writes landing in one folder at once, which the engine's own concurrency provokes), waiting what
  the body's `retry_after` or the `Retry-After` header asks for — or, where neither says, a wait that doubles from 2 s
  to a ceiling of 32 s over six attempts, so that an outage of a minute or so is sat out — plus a little jitter
  (several transfers are in flight at once and would otherwise all come back together and be limited again). A first
  sync of a whole library *will* be rate limited; treating that as a failure would mean a library that can never
  finish its first sync.
- A 409 whose summary says `insufficient_space` is a full account and becomes `SyncRemoteStorageFullException`,
  which ends the run; any other 409 is the refusal of that one file.
- `dropbox/DropboxTransport` carries every call of the provider's — the retries and the 401 renewal below, and what a
  failure is turned into — and `dropbox/DropboxTokens` the token exchange and renewal; the provider builds both itself,
  so neither is a Koin definition.
- `dropbox/DropboxModels` — the parts of the API's answers that are read. Everything defaulted, unknown keys
  ignored: a field added on the other side must never turn into a parse failure the user sees as a broken sync.
- OAuth is **PKCE with no client secret**, which is what lets this work with no server of Campfire's own. Tokens are
  renewed inside a single `SyncCredentialsStore.update`, so the several requests of one run cannot each start a
  refresh and have the last one to finish overwrite the tokens the others are using. The store remembers only what it
  has seen stored: a write that is refused or cancelled drops the cache, and the next caller reads the storage again.
  A read the storage refuses (`LibraryStorageException`) is thrown and not cached, like a cancelled one, so a document
  without tokens is never written over tokens that could not be read for a moment.
  Dropbox may hand out a new refresh token on renewal, and dropping it would end the connection silently. Whether a
  token is still good is judged by the device's clock, which can be wrong, so a 401 is answered once with a forced
  refresh and the request sent again — a refresh only of the token that was refused, so that the transfers refused
  together share one renewal — before it is reported as a refusal that needs the account connected again.
- `auth/SyncAuthenticator.<platform>.kt` — the four ways back from a consent page. **Noticing that the user backed
  out is as much a part of each of these as the redirect itself**: a consent page lives somewhere the app cannot
  see, so an authorization that only ever waits for a redirect leaves the UI on "waiting for the browser" forever
  with no way back.
  - **iOS** uses `ASWebAuthenticationSession`, which is the only iOS API that reports a dismissal: its completion
    handler is given a null URL and an error. A cancel is `ASWebAuthenticationSessionErrorCodeCanceledLogin` and
    becomes `Cancelled` with no message, which Settings takes as the user backing out; any other error keeps its
    description and is reported as a failed connection. It also keeps the sheet inside the app and shares Safari's cookies.
    The redirect never reaches `onOpenURL`, so nothing forwards it.
  - **Android** opens the user's own browser (never a WebView: a page asking for a password has to be somewhere the
    address bar is visible) and receives `campfire://oauth` as an intent, which the launcher activity forwards
    through the public `onSyncRedirectReceived`. Nothing reports a dismissed browser, so what is watched instead is
    the app itself coming forward again through `ActivityLifecycleCallbacks`; the redirect intent brings the
    activity forward too, so it is given a short grace period to arrive before the attempt counts as abandoned. An
    abandoned attempt is `Cancelled` with no message, the user backing out rather than a failure. A
    process killed while the browser was in front gets the redirect as the intent that starts the next one; it waits
    in the same channel and `consumePendingRedirect` hands it to `restore`, which finishes the authorization the way
    the web does.
  - **Desktop** becomes a web server for the length of one authorization — a socket on `127.0.0.1:53682` whose
    address is the redirect URI. The port is fixed because a service only redirects to a URI registered with it
    character for character. The browser itself is opened through `SystemBrowser`, which the desktop shell's own URL
    opener implements, so that the consent page and the links in Settings take exactly the same path — AWT first,
    the operating system's command after it, `LinkageError` caught and the child process's output discarded — and an
    authorization whose browser never opened ends at once instead of waiting out the five minute timeout. `accept` blocks a thread and notices neither a cancelled coroutine nor an interrupt, so
    cancelling closes the socket underneath it from a sibling coroutine; `DesktopSyncAuthenticatorTest` covers that,
    since getting it wrong holds the port for five minutes and is invisible until somebody cancels. Browsers open a
    speculative second connection next to the navigation and ask for a favicon after it, so the listener reads each
    connection with a timeout of its own, answers what is not the redirect with a 404, and keeps listening until the
    redirect arrives.
  - **Web** navigates away and reads the answer out of the query string at the next start, taking it out of the
    address bar as it does so, so a reload cannot replay a spent code. Cancelling on the service's page comes back
    as `error=access_denied`, which is handled like any other refusal.
- `coverArt/CoverArtRemoteSourceImpl` — a GET of whatever address a song names, redirects followed. What comes back is
  trusted no further than its headers: an answer that is not `image/*` or is over 5 MB (read one byte past the limit
  where no length is declared) is `Missing` and never read to its end; a 408, a 429, a 5xx or a transport failure is
  `Unreachable`; any other refusal is `Missing`.
- `coverArt/CoverArtSearchTransport.kt` — what the two cover searches share: any `Throwable` of the transport turned
  into a `CoverArtSearchException`, and the answer decoded on `Dispatchers.Default`, since a recording search lists
  every release of every recording and decoding it on the main thread lands on the frames of the sheet opening.
- `musicBrainz/` — the MusicBrainz cover search. `MusicBrainzSearch` is the pure part, and tested: with an album, the release
  groups of that name (`/ws/2/release-group`, one release group standing for every edition of a record, which is what
  a cover is picked for); with only a title, the recordings, and the release groups their releases belong to, each
  once, dated by its earliest release and in the order the recordings are ranked; either narrowed by the artist. The
  values are Lucene phrases, so only a quote and a backslash are escaped. The search results carry nothing about
  covers, so every candidate's address is the Cover Art Archive's `front-250` of its release group, and a group with
  no cover is found out by its thumbnail's 404. `MusicBrainzCoverArtSearchRemoteSource` spaces its requests through
  `MusicBrainzRateLimiter` — one for the whole app, a `Mutex` and the start of the last request, 1.1 s apart, since
  MusicBrainz refuses every request for as long as a client averages more than one a second — and waits out a 503 or
  a 429 for its `Retry-After`, or for 2 s doubling to 32 s where it names none, over at most five retries, calling
  `onBusy` before each. `MusicBrainzModels` are defaulted and read with unknown keys ignored, like the Dropbox ones.
- `iTunes/` — the iTunes Search API's cover search, which needs no key and sends CORS headers on the search and the
  artwork alike. `ITunesSearch` is the pure part, and tested: the *songs* are searched with the artist and the album
  (or the title) as one term — the API's album search matches the album's name alone, so an artist in the term finds
  nothing — and the albums they are on are the candidates, each once, in the songs' order, a ` - Single` or ` - EP`
  suffix becoming the type. The artwork is the answer's 100 px address rewritten to `250x250bb.jpg`, which Apple's
  image server renders at any size; an address in any other form is not guessed at and the record is left out. It
  allows about twenty requests a minute, so it is asked without a pace of its own and a refusal is a failure.
- `crypto/` — `Pkce` (verifier, S256 challenge, state) and `dropboxContentHash` (SHA-256 of each 4 MB block,
  concatenated, hashed again), both on the `Sha256` in `:data:source:remote:api`.
- `network/` — the `HttpClient` factory, one engine per target (OkHttp, CIO, Darwin, `fetch`), `HttpClientHolder` —
  the one client, built on the first request and on `Dispatchers.Default`, so a user who never syncs or searches for
  a cover never builds it and the view model's construction never does on the main thread —, `UserAgent.kt` — the
  `Campfire/<version> ( https://github.com/pandulapeter/campfire )` every request names the app with, which MusicBrainz
  asks of every client and throttles hardest without, sent on every request since it names the app and nothing about
  the user; not in the browser, where a script cannot set it and where a header the host does not expect would turn
  a plain GET into a CORS preflight that an image host may refuse — and `Json.kt`:
  `toAsciiJsonString` exists because Dropbox takes upload and download arguments in an HTTP header, which may only
  carry ASCII, and Campfire's files are named after song titles. The browser engine reports a failed `fetch` as a
  `kotlin.Error` rather than an `Exception`, so the provider's `transport` takes any `Throwable` that is not a
  cancellation as the service not being reached.

Tested in `commonTest`, run on the desktop target: the hashing, the encoders, both cover searches' queries and parsing,
its `User-Agent`, its pace and its retries in virtual time, the cover download's refusals, and the authorization URL — get a
parameter wrong there and the user meets an error page on the service's own site with nothing in the app to say why —
and, against a Ktor `MockEngine` in virtual time, how requests answer being told to slow down, and being cancelled or
timing out, and the credentials store's cache (a cancelled read is not an answer, a failed write is not
remembered).
`desktopTest` adds the one platform piece worth testing, the loopback server's cancellation.
