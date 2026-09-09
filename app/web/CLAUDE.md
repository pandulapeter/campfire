# :app:web

Kotlin/Wasm entry point. A `wasmJs` browser target only — the one module that is not multiplatform in the other
direction.

- `CampfireWebApplication.kt` — `main()` starts Koin through the `KoinApplication` composable
  (`dataLocalSourceModule + dataRepositoryModule + domainModule + presentationModule`) inside a `ComposeViewport` and
  hosts `CampfireWebApp`. Add new Koin modules here.
- `src/wasmJsMain/resources/index.html` — the page itself. It shows a pulsing app icon until the Compose canvas takes
  over, because the `.wasm` binary is tens of megabytes and an empty black page for that long looks broken. The
  webpack output is named `campfire.js` (`outputModuleName` + `commonWebpackConfig`), which is what the page loads.

The library lives in the **Origin Private File System**, so it is per-origin and per-browser: a user's songs do not
follow them to another browser, and clearing site data deletes them. OPFS needs a secure context, which means `https`
or `localhost` — a distribution served from `file://` will start and then fail to read anything.

- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — dev server on `localhost` (prints the port).
- `./gradlew :app:web:wasmJsBrowserDistribution` — the deployable site in `build/dist/wasmJs/productionExecutable`.

The web build has no file associations and no "open with": browsers cannot register those without a service worker.
Files reach it through the picker (a hidden `<input type="file">`) or by being dropped on the page.
