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

Kotlin/Wasm entry point. A `wasmJs` browser target only — the one module that is not multiplatform in the other
direction.

- `CampfireWebApplication.kt` — `main()` starts Koin through `:app:di`'s `startCampfireDependencyGraph`, then hosts
  `CampfireWebApp` inside a `ComposeViewport`. The modules are named in `:app:di`, not here.
- `src/wasmJsMain/resources/index.html` — the page itself, and the loading screen the app is handed over from: the
  icon, the name and a **determinate** progress bar, on the same background the first composed frame paints (the
  light and dark backgrounds of `:presentation`'s `CampfireColorScheme`, picked by `prefers-color-scheme`), because
  the binaries are sixteen megabytes and an empty page for that long looks broken. The bar is real: where the build
  is kept in the browser (below) it
  measures the download of whatever the kept build is missing, against the sizes in the build manifest, before
  anything starts; where nothing can be kept, an inline script wraps `fetch` before `campfire.js` runs and counts the
  `.wasm` bodies on a clone of each response, against the total the build wrote into the page. That response goes back
  untouched, since its URL and its HTTP cache entry are what let `WebAssembly.instantiateStreaming` keep compiling as
  it downloads and let Chromium keep the compiled code for the next visit. The download owns the first 92% and the rest is a decay that only ends when Kotlin
  calls `window.campfireReady()`; `campfire.js` itself is loaded by a script the page adds once it holds the
  `campfire-library` Web Lock, which can report no progress,
  but it is 3% of a cold start. The lock is held by a promise that never settles, so one tab owns the library; a
  second gets a localized "already open" page with a Retry button that asks again in place, preserving an OAuth
  answer in the address bar. A page restored from the back/forward cache reclaims the lock in `pageshow`, and the
  page's English or Hungarian texts follow `navigator.language`. A `.wasm` or
  `campfire.js` download that fails, or an error or unhandled rejection before `campfireReady()`, stops the bar and shows a message with a
  "Try again" button that reloads the page — saying that a first visit needs a connection when the kept build was not
  complete; those listeners are removed once the app is ready.
  Before anything is downloaded, `isBrowserSupported` validates two tiny modules using Wasm GC and the original
  exception handling emitted by this Kotlin version; a browser that fails gets its own message without a retry
  button. The exception-handling probe and the named browser versions have to follow the compiler if it moves to
  `try_table`, checked in `campfire.wasm` after a Kotlin upgrade.
  Compose empties the element it is given, so it gets `#app` and the loading screen is a sibling that outlives the
  handover. `CampfireApp` waits two frames after the launch screen is taken away before it calls `onAppReady` —
  `withFrameNanos` resumes while its own frame is still being assembled — and `CampfireWebApp`
  (`presentation/src/wasmJsMain`) passes `dismissLoadingScreen()`, which calls `window.campfireReady()`, so the fade
  uncovers the app rather than an empty page. The webpack output is named `campfire.js` (`outputModuleName` +
  `commonWebpackConfig`).
- **Every touch gesture is the app's**: `index.html` gives `#app` `touch-action: none`, overriding the `pan-x pan-y`
  Compose puts on its canvas for a page it is nested in. With that, the browser takes over any drag Compose has not
  consumed a move of by the first `touchmove` — one still inside Compose's touch slop, such as a drag starting on a
  text field in a scrolling sheet — and cancels it for a page that cannot scroll. It is set on the container because
  the canvas is inside Compose's shadow root, where no selector of the page reaches; the browser intersects the
  `touch-action` of every element from the canvas up, across that boundary too.
- **The keyboard is an inset, as on the native builds, wherever the browser says where it is.** Compose reports no
  IME inset on the web, so `ProvideKeyboardInsets` (in `:presentation`'s `wasmJsMain`) asks the browser to lay the
  keyboard over the page (the VirtualKeyboard API's `overlaysContent`: Chrome, Edge, Samsung Internet), reads its
  height from `geometrychange`, animates it — the browser reports only where the keyboard ends up — and provides it as
  the `ime` of `LocalPlatformWindowInsets`, the internal local Compose's `WindowInsets` are read from (the desktop
  title bar is provided the same way). The window keeps its size, so nothing under a dialog is laid out again, and the
  shell's padding, the sheets, the editor and the dialogs keep clear of the keyboard as they do on Android. Firefox,
  which has no such API, falls back on the viewport's `interactive-widget=resizes-content`: the page, and with it the
  canvas, is shortened while the keyboard is up, which the app takes as a window that changed its size. Safari
  ignores both and keeps panning the visual viewport.
- **The page is only ever loaded as the folder it lives in.** Every screen of the app has an address of its own
  (`…/app/song/…`, see `BrowserRoutes` in `:presentation`), which is not a file: GitHub Pages answers it with the
  site's `404.html` (in the `campfire-website` repository), which sends it on to `…/app/?/song/…` with the whole path —
  and `webpack.config.d/routes.js` has the development server redirect
  the same way, for navigations whose first segment is one of the app's. The first script of `index.html` then writes
  that folder into a `<base>` before anything is fetched, and puts the address from the query string back into the
  address bar. The `<base>` is load bearing: once the app has put a deeper address up, every relative URL — the icon,
  the preloaded fonts, `campfire.js`, the binaries, the resources Compose fetches later and the storage worker the first
  write starts — would otherwise be resolved against it. A host that serves `index.html` at the deep address itself (a
  single page app fallback) breaks exactly that, since the page can no longer tell where its folder ends.
- **Links open with `noopener,noreferrer`** (`openInNewTab` in `:presentation`'s `CampfireWebApp.kt`): a song's
  `{meta: link …}` is an address somebody else may have written, and `window.open` — unlike a `target="_blank"` link —
  hands the opened page a `window.opener` it could replace this tab through, while GitHub Pages sends no
  `Cross-Origin-Opener-Policy` that would sever it. `noreferrer` keeps the app's address, which names the song file,
  out of the linked site's `Referer`.
- The page also preloads the font files `:presentation` bundles — Inter in three weights for the interface, which the
  launch screen waits for, and the two monospaced ones for tabs — so they download alongside the binaries rather than
  after them. The links are `as="fetch"` with `crossorigin`, which is what makes the Compose
  resource reader's own `fetch()` match them; `as="font"` would be downloaded a second time. They name the files by
  their path in the distribution (`composeResources/<package of Res>/font/…`), so renaming a font means renaming it
  there too.
- **Every file but the page is asked for with its own version in its query string** (`?v=<hash>`, the hash of that
  file's content, so a release leaves the addresses of the files it did not change — skiko's binary, the fonts, the
  drawables — and the browser's copies of them alone). GitHub Pages lets a browser keep any file for ten minutes by
  its address (`max-age=600`, not configurable) and a deployment replaces the files one by one, so fixed names let a
  fresh page start an old `campfire.js` whose binaries were already deleted, or a new binary with old resources. The versions are part of the build manifest (below), and a script at the
  head of `index.html` wraps `fetch` for good — only for addresses inside the page's folder, so the sync service's are
  untouched — creates the font preloads with the same versioned addresses, and exposes `window.campfireVersioned`,
  which is how `campfire.js` and `opfs-writer.js` (in `:data:source:local:implementation`) are named. The loading
  screen's counting `fetch` wraps that one and gives it back rather than the browser's own. An address that already
  carries a `v` is left alone: that is a file of another build being downloaded to be kept. The icons are versioned
  too (`FaviconEffect` and the page ask for them through `campfireVersioned`), since the versioned address is the one
  a file is kept in the browser under. On the development server there is no manifest and no version.
- `src/wasmJsMain/resources/favicon-<color>.png` — the app icon in every theme color, the app's own
  `favicon-campfire.png` included, generated by `app/generate_theme_icons.py` from `app/icons/icon-192.png`; the app's
  own is the loading screen's icon and the page's favicon until the app says otherwise. Every one of them is named by
  its color, the default too, because a browser keeps a favicon by its address: an icon that changed under the same
  name goes on showing as it was, which is also why a set of icons that is redrawn gets new names rather than being
  written over the old ones. `FaviconEffect` (in
  `:presentation`) points the favicon at the one the preferences ask for and leaves its name in local storage, and a
  script in `index.html` puts that name on the favicon and the loading screen's icon before anything else loads, so a
  returning visitor's tab never shows the app's own icon first. A name is only taken if it looks like one of these files.
  The loading screen's colors are the app's own palette's.
  **There is deliberately no web app manifest**, and the one service worker only serves the kept build (below). The
  web build is a page, not an installable app — every platform that should have an installable Campfire gets a native
  build instead.
- **The page keeps a copy of the app in the browser, so the address opens without a connection.** One Cache Storage
  cache, `campfire-web-app`, holds every file of a build under its versioned address and the page under the folder's
  address. Once the page holds the library's Web Lock (so only one tab ever writes the cache) it registers
  `src/wasmJsMain/resources/service-worker.js` and asks for `build.json` with `no-store` and an address of its own —
  `navigator.onLine` is not asked, since it says yes on a network that leads nowhere. While the question is out the
  cache is looked at once (its addresses, whether the kept page is this build's, whether the worker is active), and
  what that finds is all `keep()` and `update()` go by, the cache being asked again only after a download. The answer is
  waited for at most three seconds, or only until 0.8 s into the launch where the kept build is whole and no late answer
  named another build last time (`answerWindow` in `campfire-launch`):
  - its own build id: whatever the cache lacks of the page's own build is downloaded (all of it on a first visit,
    and even without an answer, since a slow first visit is still online), the page itself kept if it is not yet, and
    the app started;
  - another build id: that build's missing files and then its page (fetched as `index.html`, which the worker has no
    entry for) are downloaded, the page stored **last**, and the page reloaded at the same address — a Dropbox answer
    in the query string included — once per build id per tab (`campfire-reloaded-for` in session storage), so a
    deployment that keeps failing its check cannot loop;
  - no answer, or an update that failed anywhere: the kept build starts, and the next launch tries again. The
    request is left to run to its three seconds, and an answer it brings after the kept build started that names
    another build is noted (`campfire-update-waiting` in local storage), so the next launch waits the full time for
    it: a network that is always slower than 0.8 s gets a release, or the fix of a broken one, one launch later
    than a fast one rather than never. An answer in time, or the launch of the build it named, crosses the note off.

  Storing the page last is the whole commit: a kept build is the kept page plus every file its map names, so an
  interrupted update leaves the previous build whole, with some of the next one's files beside it for the next
  attempt. Every file is read in full, checked against its full SHA-256 (asked for with `cache: 'no-cache'`, since
  GitHub Pages ignores the query string and a stale CDN copy must not stay in the HTTP cache under the new address)
  and stored as a response carrying only its `Content-Type` — `application/wasm` for the binaries, whatever the host
  said, so they still compile as they stream out of the cache. A download gives up after fifteen seconds without a
  byte rather than after a total time. Once the app is up, entries the running build does not name are deleted, but
  only while the kept page is the running one. Every page only ever starts the build it was made with, so the launch
  needs no protocol version. Where there is no Cache Storage or it is refused (Firefox's private windows), and on the
  development server, the app starts from the network as it always did.

  Before starting Kotlin the page sets `window.campfireSavedForOffline`: the worker is active, the kept page is this
  build's and every file of the map is in the cache. `isAppAvailableOffline()` (`:presentation`, next to
  `requestLibraryPersistence()`) reads it, and Settings' Storage row puts it together with the library's persistence.
  It is found out again on every launch and never remembered.

  The worker decides nothing: a navigation to the folder (any query string, so an authorization answer is never a
  cache key) is answered with the kept page or the network; a navigation to a screen's address is redirected to
  `…/app/?/song/…`, the form the site's `404.html` produces, for the app's first segments only, so a bookmarked
  screen opens offline; `build.json` and the worker itself always go to the network; any other GET inside the folder
  is answered from the cache entry of its exact address, or the network; nothing outside the folder is intercepted. It
  calls `skipWaiting()` and `clients.claim()`, which is safe because it holds no state. **Two rules outlive any
  release**: the cache's layout is the contract between the worker and every page ever kept, so a different layout
  takes a new cache name; and the worker's address is permanent and registered with `updateViaCache: 'none'`, so the
  browser checks it on every navigation whatever the kept page does — a broken kept page is fixed by publishing a
  worker that sends navigations to the network, and offline support is taken away by publishing one that deletes the
  cache and unregisters itself, never by deleting the file. The pure parts — the worker's `campfireRoute` and the
  page's `<script id="campfire-launch">` (addresses, what is missing, what is stale, which step to take) — are run as
  shipped by `node --test app/web/tests/offline.test.cjs`, which `tests.yml` runs with the storage worker's test.

  Whether a binary served from the cache keeps Chromium's compiled-code cache between visits, as one from the HTTP
  cache does, is still to be measured on a warm launch in Chrome, Firefox and Safari; if not, the binaries are the
  two files worth an exception.
- `src/wasmJsMain/resources/metronome-timer.js` — the metronome's wake-up: a dedicated worker posting a message every
  25 ms between a `start` and a `stop`, which the web `AudioOutput` in `:metronome:implementation` schedules its next
  clicks on (a hidden tab's own timers run at most once a second; a worker's do not). Loaded through
  `campfireVersioned`, so the build's digest list, the kept copy and the offline launch cover it like every other file.
- `src/wasmJsMain/resources/opfs-writer.js` — the dedicated worker `OpfsFileStorage` writes through where there is no
  `createWritable()`; a request is `{ id, path: [directory segments], name, data: bytes }`. It writes in place, so it grows the
  file to the new length first (a quota refusal then comes before anything is overwritten), writes until every byte is
  in, since `write()` may write fewer than it was given, and when a write still fails it puts the previous content back
  and fails the save. A worker terminated part of the way through cannot put anything back, so every write is
  journaled: the new content goes to a fresh `<name>.campfire-tmp` first, an empty `<name>.campfire-commit` marks it
  complete, and only then is the file written in place and the two removed, marker first. A new file is created by the
  worker there too, never by the page, so a first write cut short leaves no empty file under the song's name. A marker found later is
  played back over the file; a temporary file without one is discarded. `{ id, path, recover: true }` plays back a
  whole directory, which `OpfsFileStorage` asks for before it first reads one, and a file that cannot be recovered is
  only logged, since failing it would lock the user out of the whole library. `node --test app/web/tests/opfs-writer.test.cjs`
  runs the journal against an in-memory directory, checking that the worker killed at every step it takes, and killed
  again during the recovery, always leaves the old song or the new one; `tests.yml` runs it on every pull request, every
  night and before a release. Not preloaded and not part
  of the loading screen's byte count: it is only fetched by the first write that needs it. One worker serves the page
  for as long as it is open, answering each request with its `id`, which is what the page matches the replies by.

The library lives in the **Origin Private File System**, so it is per-origin and per-browser: a user's songs do not
follow them to another browser, and clearing site data deletes them. OPFS needs a secure context, which means `https`
or `localhost` — a distribution served from `file://` will start and then fail to read anything.

- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — dev server on `localhost` (prints the port).
- `./gradlew :app:web:wasmJsBrowserDistribution` — the deployable site in `build/dist/wasmJs/productionExecutable`.

`wasmJsBrowserDistribution` is finalized by **`finishWebDistribution`**, a `FinishWebDistribution` task registered in
`app/web/build.gradle.kts` and written in `gradle/build-logic`'s `tasks/` package, where the manifest it writes is built by the
pure `webBuildManifest` and tested against a golden text (see `gradle/build-logic/CLAUDE.md`):

- It replaces the `/*{{BUILD}}*/null` token in `index.html` with the build id, the number and total size of the
  binaries, and a map of every file of the distribution but the page, `build.json` and the worker, by its path, to
  its size and the full SHA-256 of its content — the first sixteen hex digits of which are its version — so the same
  sources ask for the same addresses and a changed file asks for a new one of its own. The build id is a hash of the
  map and of the page's source (a version name is not an identity), and `build.json` carries the id, the map and the
  digest of the finished page, which the page cannot carry about itself. webpack already names the two `.wasm` files by their content; what the map adds is that a release which
  changes one leaves the other, and every file with a fixed name, at the address a browser has it cached under.
  Written as a comment followed by `null`, so the page stays valid JavaScript with the token still in it: that is the
  development server, where the bar falls back to the sizes the server advertises. The page is written from its source rather than edited in place, so running the task twice over the
  same distribution produces the same distribution.
- With `campfire.web.precompress=true` it writes a `.gz` (and a `.br`, if the `brotli` command line tool is
  installed) next to everything worth compressing. **It is off**, because the deployment is GitHub Pages, which has
  no content negotiation for precompressed files and would never serve them — it gzips on the fly instead, `.wasm`
  included, at the same ratio the task would have achieved (16 MB of distribution goes over the wire as 5.5 MB).
  Turning it on is for a host that was configured to look for the copies, and is the only way to get the 4.2 MB
  brotli would give, since GitHub Pages does not offer brotli at all.
- The production webpack has `sourceMaps = false`: the map was 1.5 MB, three times the bundle it describes, and it
  was deployed with every distribution for nobody. The development server keeps its own, which is what makes the
  debugger show Kotlin.

Sync is the one place the web platform forced a design: asking for consent navigates *away* from the running app, so
`WebSyncAuthenticator.authorize` reports `Redirected` rather than returning a redirect URI, the PKCE verifier is
written to OPFS before the app leaves, and the answer is read out of the query string at the next start (and taken
out of the address bar as it is read, so a reload cannot replay a spent code). The redirect URI is the page's own
URL, which has to be registered with the service — a deployment served from a different address needs its own entry.
It is always written as the folder the page is served from — the `<base>`, not the address bar, which names the screen
the button was pressed on — ending in `/`, with any `index.html` taken off, since the service matches it character for
character and the same page opened as `…/app/index.html` would otherwise ask for a URI nobody registered:
`https://campfire-songbook.com/app/` for the deployment and `http://localhost:8080/` for the development server are
the two entries. The deployment's is the custom domain rather than `pandulapeter.github.io/campfire-website/app/`,
which redirects to it: the page is always running on the custom domain when it asks, and the service compares the URI as a
string before any redirect could come into it.
The answer therefore always lands on the songs' address, with the code in the query string: `restore` reports that
this start up came back from a consent page (whatever the service answered) and `CampfireViewModel` opens the Library
tab of Settings, which is the screen the user pressed the button on and which gets two history entries on top of the
songs, `settings/general` and `settings/library`, since a Back from a tab other than General goes to General first. The
query string is left alone by the app's history handling until the authenticator has read it, and taken out with the
history entry's state kept. Going Back from the consent page instead reloads the page at the address it was left from.

The web build has no file associations and no "open with": browsers only register those for an installed app.
Files reach it through the picker (a hidden `<input type="file">`) or by being dropped on the page — files or a folder,
which is opened one level deep.

## The web build as the app sees it

The web build differs from the other three in where the files are. `FileStorage` has a `wasmJsMain` actual backed by
the Origin Private File System (above), so the library is a real directory tree in the browser's own storage, private
to the origin and invisible in the user's downloads. It is also the only build that has to be downloaded before it can
start, which is what the rest of this file is about.

- The library is the only copy of the user's own work, and the browser's storage for an origin is evictable until it
  is asked not to be, so `requestLibraryPersistence()` (in `:presentation`) asks for persistence as the app starts.
  Whether it is granted is the browser's business — engagement, a bookmark, an install — so the answer is reported in
  Settings rather than insisted on: a refusal says so there, next to the export that is the way to keep a copy
  elsewhere. Clearing the site's data still removes the library, as it does for anything a page stores.
- One tab per origin owns the library through the Web Lock described under `index.html` above, which keeps OPFS from
  changing behind the running app's cached repositories.
- **The page keeps a copy of the app in the browser**: the launch, the cache, the worker and the Storage row are the
  bullet of that name above. Nothing else changes for the user: no manifest, no install prompt, no update dialog, and
  a build published while the app is open arrives on the next launch.
- OPFS, the file input and the download link are reached through `js(...)` blocks rather than through typed wrappers:
  one crossing of the Kotlin/Wasm boundary per operation is far cheaper than one per element, and several of these APIs
  have no binding. A Kotlin lambda cannot be passed into a `js(...)` block, so callbacks (file drops) come back as
  promises instead.
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS` rather than `FAIL_ON_PROJECT_REPOS` because the
  Kotlin/Wasm tooling adds the Node.js, Yarn and Binaryen download repositories to the root project; those are declared
  in settings instead.
