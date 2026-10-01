<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Proposal: offline support for the web build

Status: implemented and verified against a production distribution served locally; the checks on the deployment
and in Firefox and Safari are still owed.  
Date: 1 October 2026.

## Product decision

The web build keeps a copy of itself in the browser, so that the address opens without a connection — after the
browser was closed or the device restarted too. Every launch first asks the deployment whether a newer build exists.
If one does and it can be downloaded, that build is what starts. If the deployment cannot be reached, or the download
fails, the saved build starts instead, without a question.

This is offline support and nothing more. The web build stays a page: no web app manifest, no install prompt, no
home-screen flow, no new name. Every platform that should have an installable Campfire has a native build.

"Newest" means what the deployment answered at launch. A build published while the app is open arrives on the next
launch; nothing reloads a running app, polls for updates or shows an update dialog. GitHub Pages' CDN may serve the
previous build for a few minutes after a deployment, which is the case today as well.

## What changes for the user

Almost nothing, on purpose.

- **The loading screen is the one there is.** Its progress bar now measures every file of the build rather than the
  two binaries. A launch with nothing new downloads one small file and starts from the saved copy. An offline launch
  looks like any other.
- **No new messages.** No "ready for offline work" snackbar, no "you are offline" banner, no update dialog. Sync and
  the cover search already say so when they cannot reach the network.
- **The one error that remains** is the existing failure message with its "Try again" button: a first visit, or a
  visit after the site's data was cleared, with no connection. Its text gains the reason: "Campfire needs a
  connection the first time it is opened in this browser."
- **Settings → Library keeps its one web-only Storage row**, which now speaks for the app as well as the library
  (below). No second row.

### The Storage row

The row already answers "will the browser keep my library?". With this change the same promise covers the saved app,
and the row is where somebody checks whether the page opens without a connection. One row with a description put
together from two halves, rather than two rows about the same storage:

| Saved app | Persistence | Description |
| --- | --- | --- |
| Complete | Granted | Campfire opens in this browser without a connection. Your browser has agreed to keep it and the library until you clear this site's data. |
| Complete | Not yet | Campfire opens in this browser without a connection. Your browser keeps it and the library, but has not yet promised to hold on to them if the device runs low on space. Bookmarking this page or using it regularly usually earns that promise; until then, an export or sync keeps a copy of the library elsewhere. |
| Not saved | Either | Campfire could not be saved in this browser, so opening it needs a connection. — followed by the existing sentence about the library for that persistence state. |

There is no "checking" state and no Retry button: the answer is known before the app starts (below), and every
launch tries again by itself. All texts need their Hungarian counterparts, in `strings.xml` and in the page.

The saved-app half reaches Kotlin as a value the page sets before it starts the app, read by a web actual next to
`requestLibraryPersistence()` in `:presentation`. Nothing below `:presentation` knows about it, and the native
builds have no row, no worker and no launch-time request.

## Design

### What is deployed

The distribution stays one flat folder, deployed by the same `rsync --delete`; `publish-web.yml` and the website's
`404.html` do not change. `finishWebDistribution` adds two files and widens the build map:

```text
/app/index.html          as today, with the build map embedded
/app/build.json          new: the same map as a file, plus the build id and the page's own digest
/app/service-worker.js   new: the worker, at an address that never changes
/app/campfire.js, *.wasm, composeResources/…, opfs-writer.js, favicon-*.png   as today
```

- The map names **every file the app can ask for** — lazy resources, both languages, the demo songs, fonts,
  licenses, `opfs-writer.js`, and now the theme icons too — with its full SHA-256. Today it keeps eight bytes of it
  (`app/web/build.gradle.kts`), which is enough for an address and not for a check. The `?v=` addresses may keep
  using the prefix.
- The **build id** is a hash of the map and of the page's source, embedded in the page and written into
  `build.json`. The version name is not an identity: two builds can share one.
- `build.json` also carries the digest of the finished `index.html`, which the page cannot carry about itself.

Nothing is kept on the server for older builds. A client downloads and checks a whole build before starting it, so
it never needs a file the deployment has since replaced. And because every file's address is its content
(`path?v=<hash>`), a release still leaves the files it did not change — skiko's binary, the fonts — where the
browser already has them.

### The saved copy

One Cache Storage cache, `campfire-web-app`, holding:

- every file of a build under its versioned address, and
- the page, under the address of the app's folder.

**The page is written last.** That is the whole commit protocol: a saved build is complete when the cached page is
there and every file its embedded map names is in the cache with the right digest. An update interrupted anywhere
leaves the previous page and all of its files in place, with some files of the next build beside them, which the
next attempt reuses. Entries the running page's map does not name are deleted after the app has started. Nothing
else on the origin is touched — not OPFS, not other caches.

Each file is read in full, checked against its digest and stored as a response carrying only its `Content-Type`.
An HTML error page is therefore never stored as a binary, and a deployment caught halfway — the map ahead of a file
— fails the check instead of producing a mixed build.

### The worker

`service-worker.js` is small and holds no state:

- **A navigation to the app's folder** (whatever its query string, so a Dropbox answer in it is never a cache key):
  the cached page if there is one, otherwise the network.
- **A navigation to a screen's address** (`…/app/song/…`): a redirect to `…/app/?/song/…`, the form the website's
  `404.html` already produces. The page and its `<base>` logic stay exactly as they are, online and offline, and a
  bookmarked song opens without the server. Only for first segments that are the app's routes, as the development
  server's `routes.js` does.
- **`build.json` and `service-worker.js`**: always the network.
- **Any other GET inside the folder**: the cache entry with that exact address, otherwise the network.
- **Everything else** — Dropbox, the cover hosts, the rest of the website — is not intercepted.

It registers with the folder as its scope, and calls `skipWaiting()` and `clients.claim()`. That is safe here
because the worker decides nothing: which build runs is the page's business, and a replaced worker serves the same
cache the same way.

Two rules follow from service workers outliving everything else:

- **The cache layout is the contract** between the worker and every page that will ever be cached. Changing it
  means a new cache name, never a new meaning for the old one.
- **The worker's address is permanent**, and the worker is the escape hatch. The browser checks it for changes on
  every navigation on its own, independently of the cached page, so a page whose update logic turned out broken is
  fixed by publishing a worker that sends navigations to the network. Removing offline support one day means
  publishing a worker that deletes the cache and unregisters itself — not deleting the file.

The development server has no build map, and so no worker and no cache.

### A launch

The page runs the same steps wherever it came from — the network on a first visit, the cache afterwards:

1. As today: the favicon, the `<base>`, the browser check, the `campfire-library` Web Lock. Holding the lock before
   anything is downloaded also means only one tab ever writes the cache.
2. Register the worker, if the browser has one to offer.
3. Ask for `build.json`, past every cache (`no-store` and a query string unique to the launch), giving up after
   three seconds. `navigator.onLine` is not consulted: it says yes on a network that leads nowhere.
4. Then one of:

| The answer | What happens |
| --- | --- |
| The page's own build id | Fetch and check whatever the cache is missing — everything, on a first visit — store the page if it is not there yet, and start. |
| Another build id | Download and check the files the cache lacks, then the new page; store the page last; reload once. The reload is served the new page from the cache and arrives in the first row. |
| No answer in time, or the update fails anywhere | Start the page's own build from the cache. The next launch tries again. |
| No answer, and the page's own build is not complete either | The existing error with "Try again". |

A download gives up when no byte has arrived for fifteen seconds, rather than after a total time a slow connection
could not meet. The reload happens at most once per build id (a note in session storage), so a deployment that keeps
failing its check cannot loop. It keeps the address as it is, so a Dropbox answer in the query string survives it
and is still read once by the authenticator.

Because a new page is just one more file of the new build, the bootstrap needs no protocol version: every page only
ever starts the build it was made with.

Where the browser has no service worker or no Cache Storage (Firefox's private windows), or refuses the space, the
app starts over the network exactly as it does today and the Storage row says it was not saved.

### Readiness

Before starting Kotlin the page knows whether the saved copy is complete: the worker is active, the page is cached,
and every file of its map was found or stored with the right digest in this launch. That one value is what the
Storage row shows. It is computed on every launch and never remembered.

### To measure

The loader today hands `WebAssembly.instantiateStreaming` the network's own response, which is what lets Chromium
keep the compiled code between visits. A response from the worker's cache must still compile as it streams
(it carries `application/wasm`), but whether the compiled code is kept has to be measured on a warm launch in
Chrome, Firefox and Safari. If it is not, the binaries are the two files worth an exception.

## What the user can count on

After one visit with a connection, the address opens in that browser without one, on the songs or on a bookmarked
screen, and everything that works on files works: reading, editing, transposing, setlists, import and export, the
covers already downloaded. Sync, connecting Dropbox, new covers and the cover search need a connection, as they do
everywhere.

Another browser or profile needs its own first visit. Clearing the site's data removes the saved app together with
the library. Safari also deletes everything a site stored once it has gone unvisited for seven days of browser use,
which is already true of the library today. So the saved app is exactly as safe as the library next to it — which
is why they share a row.

## Documentation

Once it is verified on the deployment:

- README: the offline sentence gains "the web app after its first visit".
- `CLAUDE.md` (Web) and `app/web/CLAUDE.md`: replace "deliberately no web app manifest and no service worker" with
  "no manifest; a worker that only serves the saved build", and describe the build id, the cache and the launch.
- `documentation/TO_DO.md`: the "Offline PWA" entry goes.

## Implementation order

1. `finishWebDistribution`: full digests, the icons in the map, the build id, `build.json`.
2. `service-worker.js`, with a Node test of its routing next to the storage worker's.
3. The launch steps in `index.html`: the check, the download with progress, the commit, the reload, the cleanup.
4. The readiness value, the Storage row's texts in both languages, the error text in the page.
5. Verification on a production distribution served locally, then on the deployment; the documentation after that.

## Acceptance checks

The worker's routing and the "is this build complete" decision are pure and get Node tests, run by `tests.yml` with
the existing one. The rest is checked by hand in Chrome, Firefox and Safari (iOS included) against a production
distribution.

| Scenario | Expected |
| --- | --- |
| First visit, online | The app starts; the Storage row says it opens without a connection. |
| First visit, offline | The error with "Try again"; nothing claims to be saved. |
| Browser closed, connection off, address opened | The app and the library open with no successful request. |
| A bookmarked song or Settings address, offline | That screen opens. |
| Online, nothing new | One small request; every file comes from the cache. |
| A new build published, then a launch online | The new build starts; the old app's Kotlin never runs. Unchanged files are not downloaded again. |
| `build.json` is ahead of a file on the CDN | The check fails, the saved build starts, the next launch updates. |
| The connection drops halfway through an update | The saved build starts; the next attempt reuses what arrived. |
| Connected to a network that leads nowhere | The saved build starts after three seconds. |
| Storage refused, or no service worker | The app runs as today; the row says it could not be saved. |
| Return from Dropbox, with an update waiting | The answer survives the reload and is used once; no cache key holds it. |
| A second tab during an update | The "already open" page, as today. |
| The cache emptied by hand, the library kept | The next online launch saves the app again; the row follows. |
| Back/forward cache restore, or reconnecting mid-edit | Nothing reloads. |
| Native builds | No row, no worker, no launch-time request. |

## References

- [Offline support for Compose Multiplatform web apps](https://terrablog.pages.dev/blog/offline-support-compose-multiplatform-web-apps/)
  — the precaching worker generated at build time that this design starts from; its warning about workers that
  cannot be taken back is the reason for the two rules above.
- [web.dev: the service worker lifecycle](https://web.dev/articles/service-worker-lifecycle)
- [MDN: Request.cache](https://developer.mozilla.org/en-US/docs/Web/API/Request/cache)
- [MDN: Navigator.onLine](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/onLine)
- [MDN: storage quotas and eviction criteria](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)
