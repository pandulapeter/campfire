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

- `CampfireWebApplication.kt` — `main()` starts Koin through the `KoinApplication` composable
  (`dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule + domainModule + presentationModule`) inside a `ComposeViewport` and
  hosts `CampfireWebApp`. Add new Koin modules here.
- `src/wasmJsMain/resources/index.html` — the page itself, and the loading screen the app is handed over from: the
  icon, the name and a **determinate** progress bar, on the same background the first composed frame paints (the
  `CampfireColorSchemes` palettes, picked by `prefers-color-scheme`), because the binaries are sixteen megabytes and
  an empty page for that long looks broken. The bar is real: an inline script wraps `fetch` before `campfire.js`
  runs and reads the `.wasm` bodies through a counting stream, against the total the build wrote into the page (see
  the build manifest below). The headers are carried over to the replacement response, so it still says
  `application/wasm` and `WebAssembly.instantiateStreaming` keeps compiling as it downloads. The download owns the
  first 92% and the rest is a decay that only ends when Kotlin calls `window.campfireReady()`; `campfire.js` itself
  is loaded by a `<script>` tag, which can report nothing, but it is 3% of a cold start.
  Compose empties the element it is given, so it gets `#app` and the loading screen is a sibling that outlives the
  handover. `DismissLoadingScreen` in `CampfireWebApplication.kt` waits two frames before reporting ready —
  `withFrameNanos` resumes while its own frame is still being assembled — so the fade uncovers the app rather than an
  empty page. The webpack output is named `campfire.js` (`outputModuleName` + `commonWebpackConfig`).
- `src/wasmJsMain/resources/icon-192.png` — the loading screen's icon and the page's favicon, and the only image the
  web build has. A palette PNG whose alpha channel is untouched: quantising the colours alone leaves the antialiased
  edge and the soft shadow exact and still cuts the file to a fifth, which matters because it is on the critical path.
  **There is deliberately no web app manifest and no service worker.** The web build is a page, not an installable
  app — every platform that should have an installable Campfire gets a native build instead.

The library lives in the **Origin Private File System**, so it is per-origin and per-browser: a user's songs do not
follow them to another browser, and clearing site data deletes them. OPFS needs a secure context, which means `https`
or `localhost` — a distribution served from `file://` will start and then fail to read anything.

- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — dev server on `localhost` (prints the port).
- `./gradlew :app:web:wasmJsBrowserDistribution` — the deployable site in `build/dist/wasmJs/productionExecutable`.

`wasmJsBrowserDistribution` is finalized by **`finishWebDistribution`**:

- It replaces the `/*{{BUILD}}*/null` token in `index.html` with the number and total size of the binaries, which is
  what its progress bar measures against. Written as a comment followed by `null`, so the page stays valid JavaScript
  with the token still in it: that is the development server, where the bar falls back to the sizes the server
  advertises. The page is written from its source rather than edited in place, so running the task twice over the
  same distribution produces the same distribution.
- It writes a `.gz` (and a `.br`, if the `brotli` command line tool is installed) next to everything worth
  compressing, for hosts that serve those: 16 MB of distribution is 5.5 MB gzipped and 4.2 MB with brotli. Set
  `campfire.web.precompress=false` to skip it — it costs about half a minute — and note that a host that does not
  serve the precompressed copies simply ignores them.
- The production webpack has `sourceMaps = false`: the map was 1.5 MB, three times the bundle it describes, and it
  was deployed with every distribution for nobody. The development server keeps its own, which is what makes the
  debugger show Kotlin.

Sync is the one place the web platform forced a design: asking for consent navigates *away* from the running app, so
`WebSyncAuthenticator.authorize` reports `Redirected` rather than returning a redirect URI, the PKCE verifier is
written to OPFS before the app leaves, and the answer is read out of the query string at the next start (and taken
out of the address bar as it is read, so a reload cannot replay a spent code). The redirect URI is the page's own
URL, which has to be registered with the service — a deployment served from a different address needs its own entry.
Nothing about which screen the user was on survives that trip, because the navigation state is in memory and never in
the URL: `restore` reports that this start up came back from a consent page (whatever the service answered) and
`CampfireViewModel` opens Settings, which is the screen the user pressed the button on.

The web build has no file associations and no "open with": browsers cannot register those without a service worker.
Files reach it through the picker (a hidden `<input type="file">`) or by being dropped on the page.
