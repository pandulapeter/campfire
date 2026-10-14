<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:web

Kotlin/Wasm entry point (`wasmJs` browser target only). `CampfireWebApplication.kt` starts Koin and hosts
`CampfireWebApp` in a `ComposeViewport`. The webpack output is named `campfire.js`.

## Storage

- The library is in the **Origin Private File System**: per origin and per browser, deleted with the site's data.
  Needs a secure context (`https` or `localhost`); a distribution opened from `file://` starts and then reads nothing.
- Storage is evictable, so `requestLibraryPersistence()` (`:presentation`) asks for persistence at start; the result is
  reported in Settings' Storage row, not insisted on.
- **One tab owns the library**: the page takes the `campfire-library` Web Lock (held by a never-settling promise) before
  loading `campfire.js`, so OPFS never changes behind a running app's cached repositories. A second tab gets a localized
  "already open" page whose Retry asks again in place (keeping an OAuth answer in the address). A page restored from
  the back/forward cache reclaims the lock in `pageshow` and reloads if another tab holds it or another page launched
  the app meanwhile (`campfire-library-claim` in local storage).
- OPFS, the file input, and the download link go through `js(...)` blocks rather than typed wrappers (one boundary
  crossing per operation, and some APIs have no binding). A Kotlin lambda can't enter a `js(...)` block, so callbacks
  come back as promises.

### `opfs-writer.js`

The worker `OpfsFileStorage` writes through where there is no `createWritable()`. Request
`{ id, path, name, data }`, reply matched by `id`; one worker per page, fetched on first need (not preloaded).
- Grows the file first (a quota refusal comes before any overwrite); loops until every byte is written (`write()` may
  write fewer).
- **Journaled**: new content to `<name>.campfire-tmp`, then an empty `<name>.campfire-commit`, then written in place,
  then both removed, marker first. A marker found later is replayed; a tmp without one is discarded. New files are
  created by the worker after the commit marker, never by the page, so a cut-short first write leaves no empty song.
- `{ id, path, recover: true }` replays a directory; `OpfsFileStorage` asks before first reading one. An unrecoverable
  file is only logged, since failing would lock the user out of the library.
- Tested by `node --test app/web/tests/opfs-writer.test.cjs` (killed at every step, and during recovery).

## `index.html`

### Loading screen

- Icon, name, and a **determinate** progress bar on the first frame's background (`CampfireColorScheme`'s light/dark by
  `prefers-color-scheme`); the binaries are ~16 MB. Texts follow `navigator.language` (English/Hungarian).
- With a kept build, the bar measures downloading what it lacks against the manifest sizes. Without one, an inline
  script wraps `fetch` and counts `.wasm` bodies on a clone of each response — the response itself must stay untouched,
  since its URL and HTTP cache entry let `instantiateStreaming` compile while downloading and let Chromium cache the
  compiled code. The download is 92%; the rest decays until Kotlin calls `window.campfireReady()`.
- A failed `.wasm`/`campfire.js` download, or an error/unhandled rejection before ready, shows "Try again" (which
  reloads). Listeners are removed once ready.
- `isBrowserSupported` validates two tiny modules (Wasm GC and the legacy exception handling this Kotlin emits). **If the
  compiler moves to `try_table`, update the probe and the named browser versions** — check `campfire.wasm` after a
  Kotlin upgrade.
- Compose empties the element it gets, so it gets `#app` and the loading screen is a sibling. `CampfireApp` waits two
  frames before `onAppReady` (`withFrameNanos` resumes while its own frame is still being assembled), which calls
  `dismissLoadingScreen()`.

### Touch and keyboard

- `#app` has `touch-action: none`, overriding Compose's `pan-x pan-y`, so the browser never takes a drag still inside
  Compose's touch slop. Set on the container because the canvas is in Compose's shadow root.
- Compose reports no IME inset on the web, so `ProvideKeyboardInsets` (`:presentation`'s `wasmJsMain`) uses the
  VirtualKeyboard API's `overlaysContent` (Chrome, Edge, Samsung Internet), reads `geometrychange`, animates it, and
  provides it as the `ime` of `LocalPlatformWindowInsets`; the window keeps its size. Firefox falls back on the viewport's
  `interactive-widget=resizes-content` (the canvas shrinks). Safari ignores both and pans.

### Addresses

- **The page is only ever loaded as its folder.** Screen addresses (`…/app/song/…`, `BrowserRoutes`) aren't files:
  GitHub Pages serves the site's `404.html` (in the `campfire-website` repository), which redirects to
  `…/app/?/song/…`; `webpack.config.d/routes.js` does the same on the dev server. The first script writes the folder
  into a `<base>` and restores the address. **The `<base>` is load bearing**: every relative URL would otherwise resolve
  against the deep address. A host with an SPA fallback (serving `index.html` at deep addresses) breaks this.
- Links open with `noopener,noreferrer` (`openInNewTab`): a song's link is third-party, `window.open` hands over
  `window.opener`, GitHub Pages sends no COOP, and the referrer would name the song file.

### Versioned files

- Every file but the page is requested as `?v=<content hash>`: GitHub Pages caches for 10 minutes by address (not
  configurable) and replaces files one by one, so fixed names could mix builds. A head script wraps `fetch` (only inside
  the page's folder), creates the font preloads, and exposes `window.campfireVersioned`, through which `campfire.js`,
  `opfs-writer.js`, `metronome-timer.js`, and the favicons are named. An address already carrying `v` is left alone
  (another build's file being kept). No versions on the dev server.
- Fonts `:presentation` bundles (Inter, the monospaced ones) are preloaded `as="fetch"` with `crossorigin` so Compose's
  own `fetch()` matches (`as="font"` downloads twice). They are named by distribution path
  (`composeResources/<package>/font/…`): rename there too.
- `favicon-<color>.png` (generated by `app/generate_theme_icons.py`; edit the sources): every icon, the default too, is
  named by color, because browsers cache favicons by address — a redrawn set needs new names. `FaviconEffect` stores
  the chosen name in local storage and a script applies it before anything loads (only names that look like these
  files).
- **No web app manifest, deliberately**: the web build is a page; installable platforms get native builds.

## Offline copy

One Cache Storage cache, `campfire-web-app`, holds every file of a build under its versioned address and the page under
the folder's address. Once the page holds the Web Lock (so one tab writes the cache) it registers `service-worker.js`
and fetches `build.json` (`no-store`; `navigator.onLine` is not trusted). The cache is inspected once; the answer is
awaited up to 3 s, or 0.8 s if the kept build is whole and no late answer named another build last time
(`answerWindow` in `campfire-launch`):
- **Same build id**: download what the cache lacks (always on a first visit), keep the page, start.
- **Other build id**: download its missing files, then its page (as `index.html`), **storing the page last**, then reload
  at the same address — once per build id per tab (`campfire-reloaded-for` in session storage) to prevent loops.
- **No answer / failure**: start the kept build. A late answer naming another build is noted
  (`campfire-update-waiting`), so the next launch waits the full 3 s.

Rules:
- **Storing the page last is the commit**: a kept build = kept page + every file its map names, so an interrupted update
  leaves the previous build whole.
- Each file is checked against its full SHA-256, fetched with `cache: 'no-cache'` (Pages ignores the query string, so a
  stale CDN copy must not land under the new address), and stored with only its `Content-Type` (`application/wasm` for
  binaries, so they stream-compile). A download gives up after 15 s without a byte. Entries the running build doesn't
  name are deleted once up, only if the kept page is the running one.
- Without Cache Storage (Firefox private windows) and on the dev server, the app starts from the network.
- `window.campfireSavedForOffline` is computed every launch, read by `isAppAvailableOffline()` for the Storage row.

`service-worker.js` decides nothing: folder navigations (any query) → kept page or network; a screen address → redirect
to the `?/` form (app segments only); `build.json` and the worker → network; other GETs inside the folder → exact cache
entry or network; nothing outside the folder. `skipWaiting()` + `clients.claim()` is safe because it is stateless.
**Two rules outlive any release**:
- The cache layout is a contract with every page ever kept; a new layout takes a new cache name.
- The worker's address is permanent, registered with `updateViaCache: 'none'`. Fix a broken kept page by publishing a
  worker that sends navigations to the network; remove offline support by publishing one that deletes the cache and
  unregisters — never by deleting the file.

`node --test app/web/tests/offline.test.cjs` runs `campfireRoute` and `<script id="campfire-launch">` as shipped.

Unmeasured: whether a cached binary keeps Chromium's compiled-code cache; if not, the binaries are worth an exception.

## Other resources

- `metronome-timer.js`: a worker posting every 25 ms between `start` and `stop`, which the web `AudioOutput` schedules
  on (a hidden tab's own timers run at most once a second).

## Build (`finishWebDistribution`)

`wasmJsBrowserDistribution` is finalized by `FinishWebDistribution` (`gradle/build-logic`'s `tasks/`; the manifest is
the pure, golden-tested `webBuildManifest`):
- Replaces `/*{{BUILD}}*/null` in `index.html` with the build id, binary count and size, and a map of every file (but the
  page, `build.json`, and the worker) to size and SHA-256 (first 16 hex digits = version). Build id = hash of the map and
  the page source. `build.json` carries id, map, and the finished page's digest. The token is valid JS so the dev server
  works. The page is written from source, so the task is idempotent.
- `campfire.web.precompress` is **off**: GitHub Pages doesn't serve precompressed files (it gzips on the fly). Only for a
  host configured to look for `.gz`/`.br`.
- Production `sourceMaps = false` (deployed for nobody); the dev server keeps them.
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS`, since the Kotlin/Wasm tooling adds the Node.js, Yarn,
  and Binaryen repositories to the root project.

## Sync authorization

- Consent navigates away from the app, so `WebSyncAuthenticator.authorize` reports `Redirected`, the PKCE verifier is
  written to OPFS first, and the code is read from the query string at next start and removed (no replay on reload).
- The redirect URI is the `<base>` folder (not the address bar), ending in `/`, without `index.html` — matched
  character for character. Registered: `https://campfire-songbook.com/app/` (the custom domain, not the
  `github.io` address that redirects to it) and `http://localhost:8080/`.
- The answer lands on the songs address; `restore` reports the return and `CampfireViewModel` reopens where the button
  was pressed (`SyncConsentOrigin` in session storage): Settings' Library tab (pushing `settings/general` then
  `settings/library`) or the onboarding's Dropbox page. App history handling leaves the query string alone until the
  authenticator has read it.

## Files in

No file associations or "open with" (browsers only offer them to installed apps). Files come through a hidden
`<input type="file">` or a drop (files, or a folder one level deep).
