# Fetch covers only from public `https` hosts, and remember an address that answered with no cover on the device

**Challenged:** amended — the remote test builds its client with `configureClient` so a plugin missing from production fails it; the captive-portal note is corrected (a refused `https` → `http` redirect returns the 3xx, which is `Missing` and so persisted, but only the named host itself can send one over TLS); the clear-cache recovery is stated with its limit (Settings shows the row only while copies take space, and markers take none); the docs note the in-flight clear race is unchanged.
**Kind:** privacy / security · **Severity:** low-medium · **Platforms:** all (the LAN request: desktop for `http`, every native platform for `https`, iOS also plain `http` to an IP literal)
**Files:**
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceImpl.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/HttpClientConfiguration.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/PublicAddress.kt` (new)
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/network/PublicAddressTest.kt` (new)
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/coverArt/CoverArtRemoteSourceTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/CoverArtRepositoryImplTest.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/CoverArtLocalSource.kt` (KDoc only)
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/CoverArtLocalSourceImplTest.kt`
- `CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`,
  `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`

## Problem

A song file's `{meta: cover …}` address is fetched automatically, from any host, and a host that answers with no
cover is asked again on every launch. A song from a stranger's shared link (or any import) is enough to make
every recipient's device call an address of the stranger's choosing.

The address is taken if it looks like `http(s)` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProMetaItems.kt:131-134`):

```kotlin
fun webUrl(value: String): String? = value.trim().takeIf { url ->
    (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) &&
            url.substringAfter("//").isNotEmpty() && url.none { it.isWhitespace() }
}
```

and fetched with no other check (`CoverArtRemoteSourceImpl.kt:42-45`):

```kotlin
override suspend fun downloadCoverArt(url: String): CoverArtDownload {
    if (parseUrl(url) == null) return CoverArtDownload.Missing
    return try {
        httpClientHolder.client().prepareGet(url) { timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS } }.execute { response ->
```

Redirects are followed on every hop (`HttpClientConfiguration.kt:21-22`, "Redirects are followed"), so a public
`https` address can also redirect to `https://192.168.1.1/…` or `https://localhost:8443/…` (Ktor's `HttpRedirect`
refuses only an `https` → `http` downgrade).

What answered with no cover is remembered in memory only (`CoverArtRepositoryImpl.kt:77-78`, `:158-161`):

```kotlin
/** When each address that failed may be asked again, null for never in this session. */
private val failures = mutableMapOf<String, ComparableTimeMark?>()
…
CoverArtDownload.Missing -> CoverArtResult.Missing.also { mutex.withLock { failures[url] = null } }
CoverArtDownload.Unreachable -> CoverArtResult.Unreachable.also {
    mutex.withLock { failures[url] = environment.timeSource.markNow() + UNREACHABLE_RETRY_DELAY }
}
```

while only an image is written to `covers/` (`:154-155`), and `load` (`:141`) asks the network whenever no copy is
there. Cover art is on by default; the fetch fires when a song's row is drawn (`SongListRow.kt:100`,
`SetlistSongRow.kt:353`), and in the editor's bar of a pasted or shared song before it is saved
(`LoadedSongEditor.kt:252`).

Verified at 5514fcf76:

1. **Presence beacon: true.** A 404 or a non-image answer is `Missing`, forgotten with the process, so the host is
   asked once per launch (per process on Android/iOS) for as long as the song is in the library. A 408/429/5xx or a
   transport failure is `Unreachable`, asked again when the row is drawn again a minute later or when a cover search
   starts (`:127-131`); nothing retries on a timer, so "every minute while shown" means "every time a row with it is
   composed again after a minute" (scrolling back, returning to the list). Each request carries the device's IP and,
   on Android, iOS, and desktop, `User-Agent: Campfire/<versionName> ( https://github.com/pandulapeter/campfire )`
   (`network/UserAgent.kt`; not sent on the web). The root `CLAUDE.md` line 26, "downloaded once from the address a
   song file names, then kept on the device", is false for any address that never yields an image.
2. **LAN / localhost GET from the desktop: true.** The desktop engine is CIO
   (`HttpClientFactory.desktop.kt`), which allows plain `http`, so `http://192.168.1.1/…` or
   `http://127.0.0.1:<port>/…` gets a blind GET with the `User-Agent` above. Android refuses cleartext (target SDK =
   compile SDK, no `network_security_config`); the web's mixed-content rule refuses `http` from the `https` page.
   On every native platform an `https` LAN or loopback address (or a redirect to one) is still asked.
3. **iOS Local Network prompt: plausible, unconfirmed.** The Info.plist has no `NSAppTransportSecurity` and no
   `NSLocalNetworkUsageDescription`. ATS does not apply to IP-literal hosts (so `http://192.168.1.1/x.jpg` is likely
   sent on iOS too, contrary to the remote module's `CLAUDE.md` "plain `http://` is refused on … iOS"), and a
   request to a local address on iOS 14+ raises the system's Local Network permission prompt. Not run on a device;
   the fix removes the request either way.

Real cover addresses do not need any of this: the Cover Art Archive (`https://coverartarchive.org/…`, redirecting to
`https://archive.org/…` and `https://*.archive.org/…`) and iTunes (`https://is1-ssl.mzstatic.com/…`) are public
`https` hosts by name.

## Fix

Two independent parts; both recommended. The settled rule stays: the lookup only fills the form's draft, Save is
the only write; nothing here writes a song file, and the song keeps its address (`ChordProMetaItems.webUrl` and
the parser are not touched: links and a typed address are read as before, only the automatic fetch is narrowed).

### 1. Ask only public `https` hosts, on every hop

New `network/PublicAddress.kt`:

```kotlin
/**
 * Whether [url] may be asked without the user asking: `https`, and a host named in public DNS. Refused: plain `http`
 * (Android, iOS for most hosts, and the web refuse it already), an IP literal of either family, a name without a dot
 * (`localhost`, an intranet name, a number the resolver reads as an address), and the suffixes kept for local
 * networks. A public name that resolves to a private address is not caught; the request is a blind GET either way.
 */
internal fun isPublicHttpsAddress(url: Url): Boolean {
    if (url.protocol != URLProtocol.HTTPS) return false
    val host = url.host.lowercase().trimEnd('.')
    if (host.isEmpty() || ':' in host || host.startsWith('[') || '.' !in host) return false
    val lastLabel = host.substringAfterLast('.')
    // A public top-level domain is never numeric, so a numeric last label is an address (`127.0.0.1`, `0x7f.1`).
    if (lastLabel.all { it.isDigit() } || lastLabel.startsWith("0x")) return false
    return LOCAL_SUFFIXES.none { host == it || host.endsWith(".$it") }
}

private val LOCAL_SUFFIXES = listOf("local", "localhost", "internal", "lan", "home.arpa")
```

Applied on every request a cover download sends, redirect hops included, through a request attribute and a client
plugin hooked on `Send` (which `HttpRedirect`, installed before the user plugins, calls once per hop; `takeFrom` copies
the attribute onto each follow-up):

```kotlin
/** Marks a request whose every hop must be [isPublicHttpsAddress]; a hop that is not ends it with [RefusedAddressException]. */
internal val PublicHostsOnly = AttributeKey<Unit>("PublicHostsOnly")

internal class RefusedAddressException : IllegalStateException("Not a public https address.")

internal val PublicHostsOnlyPlugin = createClientPlugin("PublicHostsOnly") {
    on(Send) { request ->
        if (request.attributes.contains(PublicHostsOnly) && !isPublicHttpsAddress(request.url.build())) throw RefusedAddressException()
        proceed(request)
    }
}
```

installed in `configureClient` (`HttpClientConfiguration.kt:37`) next to `HttpTimeout`. In
`CoverArtRemoteSourceImpl.downloadCoverArt`:

```kotlin
val address = parseUrl(url)?.takeIf(::isPublicHttpsAddress) ?: return CoverArtDownload.Missing
return try {
    httpClientHolder.client().prepareGet(address) {
        attributes.put(PublicHostsOnly, Unit)
        timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS }
    }.execute { … }
} catch (exception: RefusedAddressException) {
    CoverArtDownload.Missing // A redirect into the local network is the address's answer, not the network failing.
} catch (exception: CancellationException) { … }
```

`Missing`, not `Unreachable`, so part 2 remembers it and it is never asked again. On the web the browser follows
redirects itself and the `Send` hook sees only the first address; the browser's own private-network rules cover the
rest there. The import download and sync do not carry the attribute (an import is an address the user opened;
Dropbox is a fixed host).

Alternatives considered: refusing in `ChordProMetaItems.webUrl` (rejected: it also reads links and typed addresses,
and would hide the address from the song itself); an "ask before the first download from an unknown host" prompt
(rejected for now: a new UI, a per-host allow list in preferences, and a question nobody can answer well; the
gate above already removes the LAN request, and part 2 the beacon for every address that never yields an image).

### 2. Remember a `Missing` answer in `covers/` (makes "downloaded once" true)

An address that answered with no cover gets an empty copy under its key: the remote source never returns an empty
image (`bytes.isEmpty() … -> CoverArtDownload.Missing`, `CoverArtRemoteSourceImpl.kt:56`), so an empty file cannot
be mistaken for one. The existing prune (`keepOnlyCoverArt`, `:89-98`) deletes it once no song names the address,
and Settings → Library's clear (`clearCoverArtCache`) deletes it too, which is the user's way to have every cover
asked again. Out of device backups like every copy (`saveCoverArt` already marks it).

`CoverArtRepositoryImpl.kt`, `load` (`:141`):

```kotlin
coverArtLocalSource.loadCoverArt(key)?.let { bytes -> if (bytes.isEmpty()) CoverArtResult.Missing else CoverArtResult.Image(bytes) }
    ?: downloadSlots.withPermit { … }
```

and `fetch` (`:158`):

```kotlin
CoverArtDownload.Missing -> CoverArtResult.Missing.also {
    mutex.withLock { failures[url] = null }
    // Kept across launches, so an address that never yields a cover is asked once, not on every start.
    coverArtLocalSource.saveCoverArt(key, ByteArray(0))
}
```

The in-memory `failures[url] = null` stays (it spares the file read within the session). Update the KDoc of
`CoverArtLocalSource` ("holds nothing but the image's bytes", plus: an empty copy means the address answered with no
cover) and of `CoverArtRepositoryImpl`.

**Captive portals:** with part 1, every cover is `https`, so a portal cannot answer for the named host without
failing TLS, which is `Unreachable`, never persisted. A redirect from `https` to `http` is refused by `HttpRedirect`,
which then returns the 3xx itself: that is `!isSuccess` and so `Missing`, persisted. Only the named host (or a hop it
chose) can send that redirect over a valid certificate, so it is that address's own answer. Without part 1 a desktop on
hotel Wi-Fi could mark plain-`http` covers missing for good, which is one more reason to land the two together.

**What a marker costs, accepted:** any non-transient answer is kept until the address leaves every song or the cache
is cleared, including a 401/403 a host sends to non-browsers now and then. The clear in Settings → Library is shown
only while `coverArtCacheSize > 0` (`LibrarySections.kt`), and markers are zero bytes, so a cache holding markers only
offers no clear; changing the song's address, or removing it, still asks again. Do not count markers in the size to
work around this: the row reports bytes. A download in flight when the cache is cleared can still write its marker
(or image) after the clear, as an image can today.

**Should `Unreachable` (408/429/5xx, transport) persist too? Recommended: no.** An offline launch makes every
uncopied cover `Unreachable`; persisting it would leave covers blank after one trip without a network, and a
Cover Art Archive 503 under load is common. The residual is that a hostile host answering 5xx still gets one
request per launch and one per redraw after a minute. If that is judged worth closing, the cheap step is doubling
`UNREACHABLE_RETRY_DELAY` per failure of the same address within the session (1, 2, 4 … minutes, capped at an hour),
in memory only, which bounds the in-session part without touching offline behaviour; persisting a dated back-off
marker is heavier and not recommended.

### Docs

- Root `CLAUDE.md` line 26: `- **Cover images**: downloaded once from the address a song file names (public \`https\`
  hosts only, never an address on the local network), then kept on the device; an address that answers with no
  cover is remembered as such.`
- `data/source/remote/implementation/CLAUDE.md`: in "Downloads", the cover bullet gains "only public `https` hosts,
  checked on every redirect hop (`isPublicHttpsAddress`, `PublicHostsOnly`); a refused hop is `Missing`"; in "What
  reaches the network", "plain `http://` is refused on Android, iOS, and the web" becomes "on Android and the web (iOS
  allows it to IP literals; covers ask `https` only)".
- `data/repository/implementation/CLAUDE.md` (The cover copies): "A non-cover answer is not asked again this
  session" → "A non-cover answer is kept as an empty copy and not asked again until no song names it or the cache is
  cleared (the clear is offered only while copies take space); an unreachable address not for a minute or until the
  next cover search starts."
- `presentation/.../ui/CLAUDE.md:106`: "Any address is accepted" → "Any address is kept; only public `https` ones are
  fetched".

## Tests

Each fails at 5514fcf76 except the two marked as guards.

- `PublicAddressTest` (new, `data/source/remote/implementation/src/commonTest/.../network/`):
  `a public https address is asked` (`https://coverartarchive.org/release-group/a/front-250`,
  `https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg`, `https://archive.org/x.jpg` → true) and
  `addresses on the device or the local network are not` (`http://coverartarchive.org/x`, `https://127.0.0.1/x`,
  `https://192.168.1.1/x`, `https://[::1]/x`, `https://localhost:8443/x`, `https://localhost./x`, `https://nas/x`,
  `https://printer.local/x`, `https://0x7f.1/x`, `https://2130706433/x` → false). Fails to compile without the fix.
- `CoverArtRemoteSourceTest`: the test client is configured with `configureClient(sendsUserAgent = true,
  hasSocketTimeout = true)` instead of its own `install(HttpTimeout)` (as `MusicBrainzRemoteSourceTest` does), so the
  tests below fail if production does not install `PublicHostsOnlyPlugin`. Two tests:
  - `an address on the local network is not asked`: the handler records every request;
    `downloadCoverArt("http://192.168.1.1/cover.jpg")` and `("https://127.0.0.1:8443/cover.jpg")` are `Missing` and
    the handler saw no request. Without the fix both are sent (and answered as images).
  - `a redirect into the local network is not followed`: first request to `URL` answers `302` with
    `Location: https://192.168.1.1/cover.jpg`, a second would answer an image; the result is `Missing` and only one
    request was made. Without the fix the redirect is followed and the result is `Image`.
- `CoverArtRepositoryImplTest`: `an address that is not a cover is not asked again after a restart`: one
  `FakeCoverArtLocalSource` shared by two `repository(...)` instances whose remote answers `Missing`; both
  `getCoverArt(URL)` are `Missing`, and the second repository's remote has `requestCount == 0`. Today the second
  instance asks again. Plus `clearing the cache asks a missing address again`: after `Missing`, `clearCoverArtCache()`,
  then `getCoverArt(URL)` makes one more request (a guard: passes today through the in-memory clear, and would fail if the marker survived the clear).
- `CoverArtLocalSourceImplTest` (desktopTest, real files): an empty copy saved under a key loads back as an empty
  array, not null, and `keepOnlyCoverArt(emptySet())` deletes it; a guard that passes today and
  pins the JVM storage's handling of the zero-byte file the marker relies on.
