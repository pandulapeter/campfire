# Build the Ktor HttpClient the first time a request needs it, off the main thread, instead of with the view model

**Kind:** performance (startup)  ·  **Severity:** medium  ·  **Platforms:** all (most on desktop)
**Lane:** S  ·  **Files:**
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/Module.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/HttpClientHolder.kt` (new),
`…/implementation/dropbox/DropboxSyncProvider.kt`, `…/implementation/coverArt/CoverArtRemoteSourceImpl.kt`,
`…/implementation/iTunes/ITunesCoverArtSearchRemoteSource.kt`,
`…/implementation/musicBrainz/MusicBrainzCoverArtSearchRemoteSource.kt`,
tests in `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/`:
`coverArt/CoverArtRemoteSourceTest.kt`, `musicBrainz/MusicBrainzCoverArtSearchRemoteSourceTest.kt`,
`iTunes/ITunesSearchTest.kt`, `dropbox/DropboxAuthorizationTest.kt`, `dropbox/DropboxRequestTest.kt`,
`network/HttpClientHolderTest.kt` (new),
`data/source/remote/implementation/CLAUDE.md`

**Challenged:** amended — the Koin module function gets an explicit return type (the compiler plugin reads definitions before bodies are resolved, and every other definition in `Module.kt` declares one); `DropboxAuthorizationTest` builds a real `createHttpClient()`, not a MockEngine, and is now named as such; the holder test is required rather than optional.

## Problem

`Module.kt:31-32` at 491c4254a:

```kotlin
@Single
internal fun httpClient(): HttpClient = createHttpClient()
```

is injected eagerly into `syncProviders(httpClient, credentialsStore)` (`Module.kt:42-53`, which builds
`DropboxSyncProvider(httpClient = httpClient, …)`), into `coverArtSearchRemoteSources(httpClient)` (`Module.kt:60-70`)
and into the `@Single internal class CoverArtRemoteSourceImpl(private val httpClient: HttpClient)`. The view model's
constructor pulls in the sync use cases → `SyncRepositoryImpl(SyncProviders)` and the cover art use cases →
`CoverArtRepositoryImpl(CoverArtRemoteSource)`, so Koin constructs the client on the main thread inside
`koinViewModel()` in the first composition — for every user, whether sync is connected and covers are on or not.

On the desktop that is `HttpClient(CIO) { configureClient(sendsUserAgent = true, hasSocketTimeout = true) }`
(`HttpClientFactory.desktop.kt:15`), installing `UserAgent` and `HttpTimeout`. Measured on the Mac: the first
construction costs 17 ms in the app (~340 classes loaded) and 67–105 ms in an isolated harness
(`scratchpad/startup-review/ktor/T.java`); on a Celeron-class 2-in-1 that is an estimated 100–400 ms of class loading
and verification on the UI thread before the first frame. It is also what prints `SLF4J(W): No SLF4J providers were
found` at every desktop start.

A disconnected user makes no request at start: `DropboxSyncProvider.isConnected()` and `storedAccount()` only read
the credentials store; requests go through `request { }` / `transport { }` (sync runs, `loadAccount`, token exchange,
revoke). A connected user's launch run makes requests from `SyncRepositoryImpl`'s scope (`Dispatchers.Default`,
`SyncRepositoryImpl.kt:125`); cover downloads run in `CoverArtRepositoryImpl`'s scope (`Dispatchers.Default`, :75);
the cover search runs from the sheet the user opens.

## Fix

Wrap the client in a holder of its own (the project's "wrap a definition in a type of its own" convention, as
`SyncProviders` does) that builds it on first use, and always off the main thread:

```kotlin
/**
 * The app's one HttpClient, built the first time a request needs it rather than when the dependency graph is: building
 * it loads a few hundred classes, which the view model's construction paid for on the main thread in the first
 * composition, for users who never connect sync or search for a cover. Built on Dispatchers.Default for the same
 * reason, whoever asks first.
 */
internal class HttpClientHolder(create: () -> HttpClient) {

    private val client = lazy(create)

    suspend fun client(): HttpClient = if (client.isInitialized()) client.value else withContext(Dispatchers.Default) { client.value }
}
```

1. `Module.kt`: replace `httpClient()` with
   `@Single internal fun httpClientHolder(): HttpClientHolder = HttpClientHolder(::createHttpClient)` — with the
   return type written out, as every other definition there has it (the Koin compiler plugin works from declarations,
   and an inferred return type is not one it can rely on). A module function rather than an annotated class, so the
   plugin has no `() -> HttpClient` constructor parameter to resolve. `syncProviders` and `coverArtSearchRemoteSources` take `httpClientHolder: HttpClientHolder`.
2. `DropboxSyncProvider`, `CoverArtRemoteSourceImpl`, `ITunesCoverArtSearchRemoteSource` and
   `MusicBrainzCoverArtSearchRemoteSource` take `httpClientHolder: HttpClientHolder` instead of `httpClient:
   HttpClient`, and every use (`httpClient.post(...)`, `httpClient.get(url)`, `httpClient.prepareGet(url)`) becomes
   `httpClientHolder.client().post(...)` etc. All call sites are already in suspend functions (`DropboxSyncProvider.kt`
   lines ~153, 250, 284, 400, 570; `CoverArtRemoteSourceImpl.kt:43`; `ITunesCoverArtSearchRemoteSource.kt:39`;
   `MusicBrainzCoverArtSearchRemoteSource.kt:53`); grep the module for `httpClient` afterwards so none is missed.
3. Tests: each test that builds a source or provider over `HttpClient(MockEngine …)` (`CoverArtRemoteSourceTest`,
   `MusicBrainzCoverArtSearchRemoteSourceTest`, `ITunesSearchTest`, `DropboxRequestTest`) passes
   `HttpClientHolder { HttpClient(MockEngine …) }` instead; `DropboxAuthorizationTest.provider()` builds over the real
   `createHttpClient()` and passes `HttpClientHolder(::createHttpClient)` (it never makes a request, so with the holder
   it no longer even builds a client). Nothing else in them changes.
4. `data/source/remote/implementation/CLAUDE.md`, the `network/` bullet: add `HttpClientHolder` — the one client, built
   on the first request and off the main thread, so a user who never syncs or searches never builds it.

Rejected alternative: injecting `Lazy<HttpClient>`. The pinned Koin compiler plugin (1.2.1) does reference
`kotlin.Lazy` in its argument generator, so it may support it, but a `Lazy` resolved on whichever thread asks first
would still build the client on the main thread when the first request starts from `viewModelScope`; the holder's
`withContext` rules that out and needs no plugin feature.

## Tests

The existing `:data:source:remote:implementation:desktopTest` suites (MockEngine-backed Dropbox, cover download,
iTunes and MusicBrainz tests) cover every request path through the holder once they are updated:
`./gradlew :data:source:remote:implementation:desktopTest`. Add one test, `network/HttpClientHolderTest.kt` in
`commonTest`: a holder whose `create` counts its calls (returning `HttpClient(MockEngine { respondOk() })`) is asked
from several coroutines at once (`List(8) { async { holder.client() } }.awaitAll()` under `runTest` or `runBlocking`
with `Dispatchers.Default`), builds once and hands every caller the same instance; and a holder that is never asked
never calls `create`.

## Manual check

- Desktop, sync not connected, covers off: start the app; the `SLF4J(W)` line no longer appears in the terminal /
  `campfire.log`, and (with a profiler or `-verbose:class | grep io.ktor`) no `io.ktor` class is loaded at start.
- Connect Dropbox, restart: the launch sync run still completes; open a cover search: both catalogues answer.
- Android and iOS: sync connect, sync run and cover search still work.
