# Add `import` to the web service worker's and the development server's list of the app's screen addresses

**Challenged:** sound

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Files:** `app/web/src/wasmJsMain/resources/service-worker.js`, `app/web/webpack.config.d/routes.js`,
`app/web/tests/offline.test.cjs`,
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt` (KDoc only),
`app/web/CLAUDE.md` (only if it lists the segments)

## Problem

`BrowserRoutes` (`:presentation`, wasmJsMain) writes `import` as the address of the import report screen:

```kotlin
CampfireDestination.ImportReport -> add(IMPORT)
...
private const val IMPORT = "import"
```

so while that screen is open the address bar shows `…/app/import`. The two lists of the app's first path segments do
not know it:

```js
// service-worker.js
// The first segments of the app's screens (BrowserRoutes in :presentation), as the development server's routes.js and
// the site's 404.html know them.
var ROUTES = ['search', 'setlists', 'settings', 'song', 'setlist'];
```

```js
// webpack.config.d/routes.js
var ROUTES = ['search', 'setlists', 'settings', 'song', 'setlist'];
```

`campfireRoute` returns `null` for a navigation whose first segment is not in `ROUTES`, which leaves it to the
network. So:

- **Offline** (the case the worker exists for): reloading the page while the import report is open, or reopening the
  tab from history, asks the network for `…/app/import`, gets nothing and shows the browser's offline error page instead
  of the kept app. Every other screen's address opens offline.
- **Development server**: the same reload is passed on to webpack's static server and gets a 404.

Online on the deployment it works, because the site's `404.html` (campfire-website repository) redirects any path under
`app/` (`segments[0] === "app" && segments.length > 1`), with no list. `index.html` has no list either. Nothing else
needs the segment: once the page is loaded, `BrowserRoutes.resolve` answers `null` for `import` (it only knows
`search`, `setlists` and `settings` as one-segment paths), which opens the songs — the intended behavior, since an
import report cannot outlive its page.

## Fix

- Add `'import'` to `ROUTES` in both `service-worker.js` and `webpack.config.d/routes.js`.
- In `BrowserRoutes.kt`'s KDoc (the list of addresses), add one sentence: a new first segment has to be added to
  `ROUTES` in `app/web`'s `service-worker.js` and `webpack.config.d/routes.js` too, or its address does not open
  offline or on the development server. The worker's comment already points back at `BrowserRoutes`.
- `app/web/CLAUDE.md` describes the redirect "for the app's first segments only" without listing them; no change
  needed unless it lists them.

The worker's address is permanent and browsers check it on every navigation, so the new list reaches already installed
pages by itself; the cache layout is unchanged, so `CACHE_NAME` stays.

## Tests

In `app/web/tests/offline.test.cjs`, extend the test "the worker sends a screen's address to the form the site's 404
page produces" with:

```js
assert.deepEqual(plain(route(`${SCOPE}import`, 'navigate')), {
    action: 'redirect',
    location: `${SCOPE}?/import`,
});
```

Run with `node --test app/web/tests/offline.test.cjs` (CI's `tests.yml` already runs it).

## Manual check

Locally imitating GitHub Pages (or on the deployment after publishing): open the web app online once so the build is
kept, import a zip or several files so the import report screen opens (`…/app/import`), go offline (DevTools →
Network → Offline), reload: the app must open (on the songs) rather than the browser's offline page. With
`./gradlew :app:web:wasmJsBrowserDevelopmentRun`, reloading on the import report must open the app rather than a 404.
