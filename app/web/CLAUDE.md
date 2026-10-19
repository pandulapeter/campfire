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

The worker `OpfsFileStorage` writes through where there is no `createWritable()`, one per page, fetched on first need.
- Grows the file first, so a quota refusal comes before any overwrite.
- **Journaled** (`.campfire-tmp`, then a `.campfire-commit` marker, then written in place), so a terminated worker
  leaves the file old or new. New files are created by the worker after the marker, never by the page, so a cut-short
  first write leaves no empty song. An unrecoverable file is only logged, since failing would lock the user out of the
  library.
- Tested by `node --test app/web/tests/opfs-writer.test.cjs` (killed at every step, and during recovery).

## `index.html`

### Loading screen

- Icon, name, and a **determinate** progress bar on the first frame's background (`CampfireColorScheme`'s light/dark by
  `prefers-color-scheme`); the binaries are ~16 MB. Texts follow `navigator.language` (English/Hungarian).
- Without a kept build, an inline script wraps `fetch` and counts `.wasm` bodies on a clone of each response — **the
  response itself must stay untouched**, since its URL and HTTP cache entry let `instantiateStreaming` compile while
  downloading and let Chromium cache the compiled code.
- A failed download, or an error before ready, shows "Try again" (which reloads).
- `isBrowserSupported` validates two tiny modules (Wasm GC and the legacy exception handling this Kotlin emits). **If the
  compiler moves to `try_table`, update the probe and the named browser versions** — check `campfire.wasm` after a
  Kotlin upgrade.
- Compose empties the element it gets, so it gets `#app` and the loading screen is a sibling. `CampfireApp` waits two
  frames before `onAppReady` (`withFrameNanos` resumes while its own frame is still being assembled), which calls
  `dismissLoadingScreen()`.

### Touch and keyboard

- `#app` has `touch-action: none`, overriding Compose's `pan-x pan-y`, so the browser never takes a drag still inside
  Compose's touch slop. Set on the container because the canvas is in Compose's shadow root.

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
- Fonts `:presentation` bundles (Inter, JetBrains Mono) are preloaded `as="fetch"` with `crossorigin` so Compose's
  own `fetch()` matches (`as="font"` downloads twice). They are named by distribution path
  (`composeResources/<package>/font/…`): rename there too. The loading screen's text is set in Inter as well, through
  `FontFace`s at the same versioned addresses, so the page and the app hand over in one font.
- `favicon-<color>.png`: every icon, the default too, is named by color, because browsers cache favicons by address —
  a redrawn set needs new names. A script applies the stored choice before anything loads.
- **No web app manifest, deliberately**: the web build is a page; installable platforms get native builds.

## Offline copy

One Cache Storage cache, `campfire-web-app`, holds every file of a build under its versioned address and the page under
the folder's address. Once the page holds the Web Lock (so one tab writes the cache) it registers `service-worker.js`
and fetches `build.json` (`no-store`; `navigator.onLine` is not trusted). The cache is inspected once; `build.json` is
waited for 3 s, or until 0.8 s into the launch when the kept build is whole (`answerWindow`). A late answer naming
another build is noted (`campfire-update-waiting`) and the next launch waits the full 3 s, so an always-slow network
still gets a release one launch later. The same build fills what the cache lacks and starts; another build is
downloaded, its page stored last, and the page reloads once per build id per tab; no answer, or a failed update, starts
the kept build. Downloads give up after 15 s without data (`STALL_TIMEOUT`), never on a total time.

Rules:
- **Storing the page last is the commit**: a kept build = kept page + every file its map names, so an interrupted update
  leaves the previous build whole.
- Each file is checked against its full SHA-256, fetched with `cache: 'no-cache'` (Pages ignores the query string, so a
  stale CDN copy must not land under the new address), and stored with only its `Content-Type` (`application/wasm` for
  binaries, so they stream-compile). Entries the running build doesn't name are deleted only if the kept page is the
  running one.
- Without Cache Storage (Firefox private windows) and on the dev server, the app starts from the network.
- `window.campfireSavedForOffline` is computed every launch, read by `isAppAvailableOffline()` for the Storage row.

`service-worker.js` decides nothing (kept page or network; nothing outside the folder), which is why `skipWaiting()` +
`clients.claim()` is safe. Its routes, which update detection depends on: a screen address is redirected to the `?/`
form; `build.json` and the worker itself always come from the network; any other GET inside the folder is answered
from its exact cache entry, else the network.
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
- Replaces `/*{{BUILD}}*/null` in `index.html` with the build id and a map of every file to size and SHA-256 (first 16
  hex digits = version), and writes `build.json`. The token is valid JS so the dev server works; the page is written
  from source, so the task is idempotent.
- `campfire.web.precompress` is **off**: GitHub Pages doesn't serve precompressed files (it gzips on the fly). Only for a
  host configured to look for `.gz`/`.br`.
- Production `sourceMaps = false` (deployed for nobody); the dev server keeps them.
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS`, since the Kotlin/Wasm tooling adds the Node.js, Yarn,
  and Binaryen repositories to the root project.

## Sync authorization

- The authorization itself is `data/source/remote/implementation/CLAUDE.md`'s OAuth section.
- The answer lands on the songs address; where it reopens is `ui/sync/CLAUDE.md`. App history handling leaves the query
  string alone until the authenticator has read it.

## Files in

No file associations or "open with" (browsers only offer them to installed apps). Files come through a hidden
`<input type="file">` or a drop (files, or a folder read to the bottom).
