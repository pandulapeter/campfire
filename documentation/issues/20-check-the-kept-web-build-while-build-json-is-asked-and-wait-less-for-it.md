# Check the kept web build while build.json is being asked for, read the cache once, and wait less for an answer when the kept build is whole

**Kind:** performance (web startup)  ·  **Severity:** medium  ·  **Platforms:** web
**Lane:** W  ·  **Files:** `app/web/src/wasmJsMain/resources/index.html`, `app/web/tests/offline.test.cjs`, `app/web/CLAUDE.md`,
`CLAUDE.md` (root, Web section: "for three seconds", and for option (a)/(b) the launch description)
**Challenged:** amended — (c) as first written ignored an answer that came after 0.8 s, so on a network that is always that slow a kept build (a broken one included) would never be replaced; a late answer naming another build now makes the next launch wait the full three seconds (`campfire-update-waiting` in local storage). And the worker's state is read again at the end whenever the early inspection found it inactive, or a first visit would report "not saved" where today it says saved.

## Problem

Every launch of the web build, warm ones included, waits for a network round trip and then for a chain of Cache
Storage calls before `campfire.js` is even requested. `index.html` at 491c4254a, `launch()` (≈ line 668):

```js
Promise.all([
    window.caches.open(CACHE_NAME).catch(function () {
        return null;
    }),
    askDeployment()
]).then(function (results) {
    …
    return keep(answer && answer.id === BUILD.id ? answer : null);
}).catch(function () {
    isSaved = false;
}).then(startApp);
```

`askDeployment()` fetches `build.json` with `no-store`, aborting after `ANSWER_TIMEOUT = 3000`. Only once it has
answered does `keep()` start, and it runs its checks one after another:

```js
function keep(answer) {
    return cachedAddresses().then(function (kept) {                       // cache.keys() #1
        return download(window.campfireLaunch.missingFiles(BUILD.files, kept, folder));
    }).catch(function () {
    }).then(function () {
        return isPageKept().then(function (isKept) {                      // match + text() of the 63 KB page #1
            …
    }).then(function () {
        return Promise.all([isWorkerActive(), isPageKept(), cachedAddresses()]);  // page #2, keys() #2
```

So a warm launch with everything kept costs one round trip (100–400 ms on a normal connection, up to the full 3 s on
a network that accepts the request and never answers — a captive portal, a train) plus five serial Cache Storage
operations, the kept page read and parsed as text twice, before the app's 19 MB start to compile. On a low-end
device each of those storage calls is slow too. Nothing of the check depends on the answer except the decision
"keep or update", which the cache state does not change.

A first visit, and the first launch after a release, download everything missing (~19 MB raw), SHA-256 it and store
it before the app starts; the binaries then compile from the cache, with no overlap between download and compile.

The policy is documented in the root `CLAUDE.md` (Web: "Every launch asks the deployment's `build.json` which build is
current (past every cache, for three seconds) … another build is downloaded, checked … and loaded once") and in
`app/web/CLAUDE.md` ("for at most three seconds"). It guards real risks: a kept build is only ever replaced by a
complete, verified one (the page stored last is the commit), a deployment that keeps failing cannot loop, and a
broken release is replaced at the very next launch because the deployment is asked **before** the kept build
starts. The fixed-address `service-worker.js` is the way out of a broken kept **page**, not of a broken build that a
page starts happily.

The pure decisions are in `<script id="campfire-launch">` and run as shipped by `node --test app/web/tests/*.cjs`
(16 tests, all passing at 491c4254a).

## Fix

**Decision needed:** how far to go. Three options; they combine.

**(c) Recommended default — no policy change beyond the timeout:**

1. In `launch()`, open the cache and start `askDeployment()` at the same moment as now, but as soon as the cache is
   open start an inspection in parallel with the answer:
   `Promise.all([cachedAddresses(), keptPageIsOurs(), isWorkerActive()])` → `{ kept, isPageKept, isWorkerActive }`,
   where `keptPageIsOurs()` is today's `isPageKept()` (read once).
2. When the inspection says the kept build is whole — the page is this build's, the worker is active and
   `missingFiles(BUILD.files, kept, folder)` is empty — stop waiting for the answer
   `ANSWER_TIMEOUT_WHEN_KEPT = 800` ms after the launch began (resolve the race with `null`; the request itself may
   finish later and is ignored). A slow network then finds a new release on the next launch, which is exactly what
   "no answer" already means today — **but only if the late answer is not thrown away**: as written the request is
   abandoned, so a device whose answer always takes more than 0.8 s (a slow mobile link, a far-away Pages edge) would
   start the kept build for ever and never see a release, nor be rescued from a broken one, which the documented policy
   ("a broken release is replaced at the very next launch") forbids. So let the request run on to its 3000 ms abort; if
   it answers after the launch moved on and `nextStep(BUILD.id, answer, null) === 'update'`, write the answer's id to
   local storage under `campfire-update-waiting` (in try/catch, like the session storage guard). The next launch reads
   it before deciding the window: while it is set and differs from `BUILD.id`, the window is 3000 ms whatever the
   inspection says; the launch that starts that build (or any launch whose answer names `BUILD.id` or arrives in time)
   removes it. A slow network thus gets a release, or the fix of a broken one, one launch later than a fast one —
   which is what the docs should say. When the kept build is not whole, keep waiting up to 3000 ms as now, since it has
   to go to the network anyway.
3. Pass the inspection into `keep()` and `update()` so they do not ask again: `keep()` downloads
   `missingFiles(BUILD.files, inspection.kept, folder)`, keeps the page only when `!inspection.isPageKept && answer`,
   and computes `isSaved` from the inspection when it downloaded and kept nothing; only after a download does it read
   `cache.keys()` again (and after `keepPage` succeeded the page is known to be kept). The worker is the exception:
   `registerWorker()` runs at the start of the same launch, so on a first visit the inspection finds it still
   installing, while today's check at the end of a 19 MB download finds it active. Whenever `inspection.isWorkerActive`
   was false, call `isWorkerActive()` again for `isSaved` (it is a registration lookup, not a Cache Storage call). `update()` uses
   `inspection.kept` for `missingFiles(answer.files, …)`.
4. Put the pure part into `campfire-launch` so the Node test covers it, e.g.
   `isComplete(files, kept, folder, isPageKept, isWorkerActive)` and `answerWindow(isComplete, updateWaiting, ownId)`
   (800 only when complete and no other build's id is waiting, else 3000). The test runs that script in a `vm`
   context holding only `window`, `URL`, `JSON` and `Object`, so these must stay pure: no timers, no storage.
5. Docs: the launch description in `app/web/CLAUDE.md` and the root `CLAUDE.md`'s Web section say the answer is
   waited for three seconds, or 0.8 s when the kept build is whole and no late answer named another build last time,
   that the cache is checked meanwhile, and (the "no answer" bullet of `app/web/CLAUDE.md`) that an answer arriving
   after the kept build started makes the next launch wait the full time. The worker-escape rule ("Two rules outlive
   any release") is untouched: nothing here changes `registerWorker()` or `service-worker.js`.

Expected gain: the storage calls overlap the round trip instead of following it (two of them disappear), and the worst
case on a network that never answers drops from 3 s to 0.8 s for every kept-build launch. A good network still pays
its round trip.

**(a) Start a whole kept build at once and look for a new one in the background** (the reviewer's first choice). With a
whole kept build, `startApp()` immediately; ask `build.json` and download a newer build afterwards, storing its page
last as now, and let the **next** launch start it. Saves the round trip entirely on warm launches. Costs and
conditions, if chosen:
- every release, fix and What's new arrives one launch later, and a broken release survives one more launch;
- the background work (download plus SHA-256 of up to 19 MB, cache writes) competes with the app's own start on
  precisely the low-end device this sweep is about, so it has to wait for `campfireReady()` — but it must also not
  depend on it, or a kept build that never becomes ready would never be replaced: start it on `campfireReady()` or
  after a fixed delay (say 15 s), whichever comes first;
- `forgetOtherBuilds()` must finish before the background download starts: today it deletes every entry the running
  build does not name while the kept page is still the running one, which would delete the new build's files as they
  arrive and leave a committed page with missing files;
- the session-storage reload guard becomes unnecessary on that path; the documented policy changes in both
  `CLAUDE.md` files.
Not recommended now: (c) already caps the wait at 0.8 s, and the remaining round trip is small against what (a)
gives up.

**(b) On a first visit, start from the network and keep the build afterwards.** Where nothing is kept, call
`startApp()` at once (the fetch wrapper already counts bytes for the bar in that mode) and run `keep(answer)` after
`campfireReady()`; its `cache: 'no-cache'` requests revalidate against the HTTP cache rather than download again.
Download and compile then overlap on the one launch a new user judges the app by. Costs: `campfireSavedForOffline` is
false for that first session (Settings' Storage row says so until the next launch), the 19 MB are read and hashed
once more after start, and the "a first visit downloads all of it before starting" wording changes. A reasonable
second step after (c), at the user's call.

## Tests

In `app/web/tests/offline.test.cjs`, for the new pure functions of `campfire-launch`:
- `isComplete` is true only when the page is kept, the worker is active and no file of the build is missing; false
  when any one of the three fails (reuse the `FILES` fixture and `launch.addressOf`).
- `answerWindow(true, null, id) === 800`, `answerWindow(false, null, id) === 3000`,
  `answerWindow(true, 'other', id) === 3000` and `answerWindow(true, id, id) === 800` (a waiting id that is this build's
  own is stale, not a reason to wait).
Run with `node --test app/web/tests/*.cjs` (`tests.yml` already does).

## Manual check

1. Build and serve the distribution the way GitHub Pages does (see `app/web/CLAUDE.md`), open it once online, reload:
   in DevTools' Network panel `campfire.js` is requested no later than `build.json`'s answer, or 0.8 s after the
   launch, whichever is first, and Application → Cache Storage still holds the whole build; Settings' Storage row
   says the app is saved.
2. With the kept build whole, throttle to "Offline": the app starts after ≤ 0.8 s from the kept copy (today it
   aborts at 3 s, or fails fast).
3. Publish (locally) a second build, reload once: it is downloaded and started on that launch as before (on a normal
   connection the answer arrives within 0.8 s), and with throttling to a 1.5 s latency it arrives on the following
   launch instead: the first reload starts the kept build and leaves `campfire-update-waiting` in local storage, the
   second waits the full three seconds, updates and clears it.
5. On a first visit (cleared site data), once it has started, Settings' Storage row says the app is saved, as today.
4. Clear site data and open the page: the first visit still downloads the whole build before starting (unless (b)
   was chosen), with the progress bar.
