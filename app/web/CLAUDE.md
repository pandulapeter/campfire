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
  `CampfireColorSchemes` palettes, picked by `prefers-color-scheme`), because the binaries are sixteen megabytes and
  an empty page for that long looks broken. The bar is real: an inline script wraps `fetch` before `campfire.js`
  runs and reads the `.wasm` bodies through a counting stream, against the total the build wrote into the page (see
  the build manifest below). The headers are carried over to the replacement response, so it still says
  `application/wasm` and `WebAssembly.instantiateStreaming` keeps compiling as it downloads. The download owns the
  first 92% and the rest is a decay that only ends when Kotlin calls `window.campfireReady()`; `campfire.js` itself
  is loaded by a script the page adds once it holds the `campfire-library` Web Lock, which can report no progress,
  but it is 3% of a cold start. The lock is held by a promise that never settles, so one tab owns the library; a
  second gets a localized "already open" page with a Retry button that asks again in place, preserving an OAuth
  answer in the address bar. A page restored from the back/forward cache reclaims the lock in `pageshow`, and the
  page's English or Hungarian texts follow `navigator.language`. A `.wasm` or
  `campfire.js` download that fails, or an error or unhandled rejection before `campfireReady()`, stops the bar and shows a message with a
  "Try again" button that reloads the page; those listeners are removed once the app is ready.
  Before anything is downloaded, `isBrowserSupported` validates two tiny modules using Wasm GC and the original
  exception handling emitted by this Kotlin version; a browser that fails gets its own message without a retry
  button. The exception-handling probe and the named browser versions have to follow the compiler if it moves to
  `try_table`, checked in `campfire.wasm` after a Kotlin upgrade.
  Compose empties the element it is given, so it gets `#app` and the loading screen is a sibling that outlives the
  handover. `DismissLoadingScreen` in `CampfireWebApplication.kt` waits two frames before reporting ready —
  `withFrameNanos` resumes while its own frame is still being assembled — so the fade uncovers the app rather than an
  empty page. The webpack output is named `campfire.js` (`outputModuleName` + `commonWebpackConfig`).
- **The page is only ever loaded as the folder it lives in.** Every screen of the app has an address of its own
  (`…/campfire/song/…`, see `BrowserRoutes` in `:presentation`), which is not a file: GitHub Pages answers it with the
  site's `404.html`, which sends it on to `…/campfire/?/song/…` — the whole path for `campfire`, where the other apps on
  the site get theirs squashed into one segment — and `webpack.config.d/routes.js` has the development server redirect
  the same way, for navigations whose first segment is one of the app's. The first script of `index.html` then writes
  that folder into a `<base>` before anything is fetched, and puts the address from the query string back into the
  address bar. The `<base>` is load bearing: once the app has put a deeper address up, every relative URL — the icon,
  the preloaded fonts, `campfire.js`, the binaries, the resources Compose fetches later and the storage worker the first
  write starts — would otherwise be resolved against it. A host that serves `index.html` at the deep address itself (a
  single page app fallback) breaks exactly that, since the page can no longer tell where its folder ends.
- The page also preloads the font files `:presentation` bundles — Inter in three weights for the interface, which the
  launch screen waits for, and the two monospaced ones for tabs — so they download alongside the binaries rather than
  after them. The links are `as="fetch"` with `crossorigin`, which is what makes the Compose
  resource reader's own `fetch()` match them; `as="font"` would be downloaded a second time. They name the files by
  their path in the distribution (`composeResources/<package of Res>/font/…`), so renaming a font means renaming it
  there too.
- **Every file but the page is asked for with the build's version in its query string** (`?v=<hash>`). GitHub Pages
  lets a browser keep any file for ten minutes by its address (`max-age=600`, not configurable) and a deployment
  replaces the files one by one, so fixed names let a fresh page start an old `campfire.js` whose binaries were already
  deleted, or a new binary with old resources. The version is part of the build manifest (below), and a script at the
  head of `index.html` wraps `fetch` for good — only for addresses inside the page's folder, so the sync service's are
  untouched — creates the font preloads with the same versioned addresses, and exposes `window.campfireVersioned`,
  which is how `campfire.js` and `opfs-writer.js` (in `:data:source:local:implementation`) are named. The loading
  screen's counting `fetch` wraps that one and gives it back rather than the browser's own. The icons are not
  versioned, since they are named by their color. On the development server there is no manifest and no version.
- `src/wasmJsMain/resources/icon-192-<color>.png` — the app icon in every theme color, the app's own gray
  `icon-192-campfire.png` included, generated by `app/generate_theme_icons.py` from `app/icons/icon-192.png`; the gray
  one is the loading screen's icon and the page's favicon until the app says otherwise. Every one of them is named by
  its color, the default too, because a browser keeps a favicon by its address: an icon that changed under the same
  name goes on showing as it was. `FaviconEffect` (in
  `:presentation`) points the favicon at the one the preferences ask for and leaves its name in local storage, and a
  script in `index.html` puts that name on the favicon and the loading screen's icon before anything else loads, so a
  returning visitor's tab never shows the gray first. A name is only taken if it looks like one of these files. The loading
  screen's colors are the gray palette's.
  **There is deliberately no web app manifest and no service worker.** The web build is a page, not an installable
  app — every platform that should have an installable Campfire gets a native build instead.
- `src/wasmJsMain/resources/opfs-writer.js` — the dedicated worker `OpfsFileStorage` writes through where there is no
  `createWritable()`; a request is `{ id, path: [directory segments], name, data: bytes }`. It writes in place, so it grows the
  file to the new length first (a quota refusal then comes before anything is overwritten), writes until every byte is
  in, since `write()` may write fewer than it was given, and when a write still fails it puts the previous content back
  and fails the save; only if that fails too is the file left damaged, and the error says so. Not preloaded and not part
  of the loading screen's byte count: it is only fetched by the first write that needs it.

The library lives in the **Origin Private File System**, so it is per-origin and per-browser: a user's songs do not
follow them to another browser, and clearing site data deletes them. OPFS needs a secure context, which means `https`
or `localhost` — a distribution served from `file://` will start and then fail to read anything.

- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — dev server on `localhost` (prints the port).
- `./gradlew :app:web:wasmJsBrowserDistribution` — the deployable site in `build/dist/wasmJs/productionExecutable`.

`wasmJsBrowserDistribution` is finalized by **`finishWebDistribution`**:

- It replaces the `/*{{BUILD}}*/null` token in `index.html` with the number and total size of the binaries, which is
  what its progress bar measures against, and the version: a hash of every other file of the distribution, names
  included, so the same sources ask for the same addresses and any change to any file asks for new ones. Written as a comment followed by `null`, so the page stays valid JavaScript
  with the token still in it: that is the development server, where the bar falls back to the sizes the server
  advertises. The page is written from its source rather than edited in place, so running the task twice over the
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
character and the same page opened as `…/campfire/index.html` would otherwise ask for a URI nobody registered:
`https://pandulapeter.com/campfire/` for the deployment and `http://localhost:8080/` for the development server are
the two entries. The deployment's is the custom domain rather than `pandulapeter.github.io/campfire/`, which
redirects to it: the page is always running on the custom domain when it asks, and the service compares the URI as a
string before any redirect could come into it.
The answer therefore always lands on the songs' address, with the code in the query string: `restore` reports that
this start up came back from a consent page (whatever the service answered) and `CampfireViewModel` opens the Library
tab of Settings, which is the screen the user pressed the button on and which gets its own history entry on top. The
query string is left alone by the app's history handling until the authenticator has read it, and taken out with the
history entry's state kept. Going Back from the consent page instead reloads the page at the address it was left from.

The web build has no file associations and no "open with": browsers cannot register those without a service worker.
Files reach it through the picker (a hidden `<input type="file">`) or by being dropped on the page — files or a folder,
which is opened one level deep.
