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

Kotlin/Wasm entry point (`wasmJs` browser target only); the webpack output is named `campfire.js`.

## Storage

- The library is in the **Origin Private File System**: per origin and per browser, deleted with the site's data.
  Needs a secure context (`https` or `localhost`); a distribution opened from `file://` starts and then reads nothing.
  Storage is evictable, so persistence is asked for at start and reported in Settings, not insisted on.
- **One tab owns the library**: the page takes the `campfire-library` Web Lock (held by a never-settling promise) before
  loading `campfire.js`, so OPFS never changes behind a running app's cached repositories. A second tab gets a localized
  "already open" page whose Retry asks again in place (keeping an OAuth answer in the address). A page restored from
  the back/forward cache reclaims the lock in `pageshow` and reloads if another tab holds it or another page launched
  the app meanwhile (`campfire-library-claim` in local storage).
- A Kotlin lambda can't enter a `js(...)` block, so callbacks come back as promises.
- `opfs-writer.js` is the worker `OpfsFileStorage` writes through where there is no `createWritable()`. It grows the
  file first, so a quota refusal comes before any overwrite. **Journaled** (`.campfire-tmp`, then a `.campfire-commit`
  marker, then written in place), so a terminated worker leaves the file old or new; new files are created by the
  worker after the marker, never by the page, so a cut-short first write leaves no empty song. An unrecoverable file is
  only logged, since failing would lock the user out of the library. Tested by
  `node --test app/web/tests/opfs-writer.test.cjs`.

## `index.html`

- The loading screen's progress bar is **determinate** (the binaries are ~16 MB): without a kept build, an inline
  script wraps `fetch` and counts `.wasm` bodies on a clone of each response — **the response itself must stay
  untouched**, since its URL and HTTP cache entry let `instantiateStreaming` compile while downloading and let Chromium
  cache the compiled code. Its texts follow `navigator.language`, and a language's block may set `dir` on the page.
- `isBrowserSupported` validates two tiny modules (Wasm GC and the legacy exception handling this Kotlin emits). **If the
  compiler moves to try_table, update the probe and the named browser versions** — check `campfire.wasm` after a
  Kotlin upgrade.
- Compose empties the element it gets, so it gets `#app` and the loading screen is a sibling. `CampfireApp` waits two
  frames before `onAppReady` (`withFrameNanos` resumes while its own frame is still being assembled).
- `#app` has `touch-action: none`, overriding Compose's `pan-x pan-y`, so the browser never takes a drag still inside
  Compose's touch slop. Set on the container because the canvas is in Compose's shadow root.

### Addresses

- **The page is only ever loaded as its folder.** Screen addresses (`…/app/song/…`, `BrowserRoutes`) aren't files:
  GitHub Pages serves the site's `404.html` (in the `campfire-website` repository), which redirects to
  `…/app/?/song/…`; `webpack.config.d/routes.js` does the same on the dev server. The first script writes the folder
  into a `<base>` and restores the address. **The `<base>` is load bearing**: every relative URL would otherwise resolve
  against the deep address. A host with an SPA fallback breaks this.
- Links open with `noopener,noreferrer`: a song's link is third-party, `window.open` hands over `window.opener`, GitHub
  Pages sends no COOP, and the referrer would name the song file.

### Versioned files

- Every file but the page is requested as `?v=<content hash>`: GitHub Pages caches for 10 minutes by address (not
  configurable) and replaces files one by one, so fixed names could mix builds. A head script wraps `fetch` (only inside
  the page's folder) and exposes `window.campfireVersioned`, through which every other file is named. An address
  already carrying `v` is left alone (another build's file being kept). No versions on the dev server.
- Bundled fonts are preloaded `as="fetch"` with `crossorigin` so Compose's own `fetch()` matches (`as="font"` downloads
  twice). They are named by distribution path (`composeResources/<package>/font/…`): rename there too. The loading
  screen is set in Inter through `FontFace`s at the same versioned addresses, so the page and the app hand over in one
  font.
- `favicon-<color>.png`: every icon, the default too, is named by color, because browsers cache favicons by address —
  a redrawn set needs new names.
- **No web app manifest, deliberately**: the web build is a page; installable platforms get native builds.

## Offline copy

One Cache Storage cache, `campfire-web-app`, holds every file of a build under its versioned address and the page under
the folder's address. Once the page holds the Web Lock (so one tab writes the cache) it registers `service-worker.js`
and fetches `build.json` (`no-store`; `navigator.onLine` is not trusted), waiting 3 s, or until 0.8 s into the launch
when the kept build is whole. A late answer naming another build is noted (`campfire-update-waiting`) so the next
launch waits the full 3 s. Another build is downloaded and the page reloads once per build id per tab; no answer, or a
failed update, starts the kept build. Downloads give up after 15 s without data, never on a total time.

- **Storing the page last is the commit**: a kept build = kept page + every file its map names, so an interrupted update
  leaves the previous build whole.
- Each file is checked against its full SHA-256, fetched with `cache: 'no-cache'` (Pages ignores the query string, so a
  stale CDN copy must not land under the new address), and stored with only its `Content-Type` (`application/wasm` for
  binaries, so they stream-compile). Entries the running build doesn't name are deleted only if the kept page is the
  running one.
- `service-worker.js` decides nothing (kept page or network; nothing outside the folder), which is why `skipWaiting()` +
  `clients.claim()` is safe. Update detection depends on its routes: a screen address is redirected to the `?/` form;
  `build.json` and the worker itself always come from the network; any other GET inside the folder is answered from
  its exact cache entry, else the network.
- **Two rules outlive any release**: the cache layout is a contract with every page ever kept (a new layout takes a new
  cache name); the worker's address is permanent, registered with `updateViaCache: 'none'`. Fix a broken kept page by
  publishing a worker that sends navigations to the network; remove offline support by publishing one that deletes the
  cache and unregisters — never by deleting the file.

## Build (`FinishWebDistribution`)

- Replaces `/*{{BUILD}}*/null` in `index.html` with the build id and a map of every file to size and SHA-256 (first 16
  hex digits = version), and writes `build.json`. The token is valid JS so the dev server works; the page is written
  from source, so the task is idempotent.
- `campfire.web.precompress` is **off**: GitHub Pages doesn't serve precompressed files (it gzips on the fly).
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS`, since the Kotlin/Wasm tooling adds the Node.js, Yarn,
  and Binaryen repositories to the root project.

## Sync, files, and shared links in

- The sync answer lands on the songs address (where it reopens: `ui/sync/CLAUDE.md`); history handling leaves the query
  string alone until the authenticator has read it.
- No file associations or "open with" (browsers only offer them to installed apps): files come through a hidden
  `<input type="file">` or a drop.
- Shared links lead to `/app/#<payload>`; no scheme is registered (only `web+` schemes can be). **The fragment is taken
  before anything rewrites the address** (the app's first history write, the sync return): `sharedLinkPayload` checks
  only its shape, then the payload joins the `campfire-shared-links` list in session storage and leaves the address by
  `replaceState`. A fragment changed under a running page is taken the same way, before the app's own `popstate`
  listener stamps the entry. An entry leaves the list only once handed to the import, so a reload neither loses nor
  repeats one.
- **A second tab** offers its links on the `campfire-shared-links` `BroadcastChannel` from the "already open" page; the
  page holding the library adds them to its own list and answers, and only then does the second tab drop them. With no
  answer in 2 s they stay for Retry.
