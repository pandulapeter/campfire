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
- `src/wasmJsMain/resources/index.html` — the page itself. It shows a pulsing app icon until the Compose canvas takes
  over, because the `.wasm` binary is tens of megabytes and an empty black page for that long looks broken. The
  webpack output is named `campfire.js` (`outputModuleName` + `commonWebpackConfig`), which is what the page loads.

The library lives in the **Origin Private File System**, so it is per-origin and per-browser: a user's songs do not
follow them to another browser, and clearing site data deletes them. OPFS needs a secure context, which means `https`
or `localhost` — a distribution served from `file://` will start and then fail to read anything.

- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — dev server on `localhost` (prints the port).
- `./gradlew :app:web:wasmJsBrowserDistribution` — the deployable site in `build/dist/wasmJs/productionExecutable`.

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
