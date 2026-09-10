# :data:source:remote:implementation

Implements `:data:source:remote:api`. Koin wiring in `Module.kt` (`dataRemoteSourceModule`). The only module in the
project that makes a network call, and the only one that sees Ktor.

Providers are registered as a `List<SyncProvider>`, and **a provider the build has no credentials for is left out of
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
- `dropbox/DropboxModels` — the parts of the API's answers that are read. Everything defaulted, unknown keys
  ignored: a field added on the other side must never turn into a parse failure the user sees as a broken sync.
- OAuth is **PKCE with no client secret**, which is what lets this work with no server of Campfire's own. Tokens are
  renewed inside a single `SyncCredentialsStore.update`, so the several requests of one run cannot each start a
  refresh and have the last one to finish overwrite the tokens the others are using. Dropbox may hand out a new
  refresh token on renewal, and dropping it would end the connection silently.
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
    since getting it wrong holds the port for five minutes and is invisible until somebody cancels.
  - **Web** navigates away and reads the answer out of the query string at the next start, taking it out of the
    address bar as it does so, so a reload cannot replay a spent code. Cancelling on the service's page comes back
    as `error=access_denied`, which is handled like any other refusal.
- `crypto/` — `Pkce` (verifier, S256 challenge, state) and `dropboxContentHash` (SHA-256 of each 4 MB block,
  concatenated, hashed again), both on the `Sha256` in `:data:source:remote:api`.
- `network/` — the `HttpClient` factory, one engine per target (OkHttp, CIO, Darwin, `fetch`), and `Json.kt`:
  `toAsciiJsonString` exists because Dropbox takes upload and download arguments in an HTTP header, which may only
  carry ASCII, and Campfire's files are named after song titles.

Tested in `commonTest`, run on the desktop target: the hashing, the encoders, and the authorization URL — get a
parameter wrong there and the user meets an error page on the service's own site with nothing in the app to say why.
