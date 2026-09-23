<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Web — manual test script

What the web build does differently from the other three: the
library is in the browser's Origin Private File System (OPFS), the app has to be downloaded before it starts, one tab
owns the library, and the browser's history is the app's back stack. Everything that behaves the same on every
platform (the editor, setlists, tags, import rules, transposition…) is in `00-core-functional.md`; run that once on the
web too if time allows, but it is not repeated here. Multi-device sync is in `07-sync-multi-device.md`; only the parts
of sync that are specific to a browser are here.

Priorities: **P0** can lose or corrupt the library or blocks the release, **P1** is visible breakage, **P2** is
polish.

## Before you start

- **Browsers.** The current Chrome, Firefox and Safari on macOS, Edge on the Windows PC, and Safari on an iPhone
  with the current iOS. For the OPFS worker fallback you also need **one browser that lacks `createWritable`**:
  Safari 18.2–18.x (a Mac still on macOS 15, an iPhone/iPad on iOS 18.2–18.x, or an iOS 18 simulator runtime). Check
  in that browser's console: `'createWritable' in FileSystemFileHandle.prototype` must print `false`. Once no such
  browser can be had, the fallback serves nobody: skip its tests and say so in the run log.
- **Three ways to run the build**, and some tests name one:
  1. **Dev server:** `./gradlew :app:web:wasmJsBrowserDevelopmentRun` → `http://localhost:8080/`. It does not rebuild
     on source changes; restart it after every pull.
  2. **Local production copy (imitates GitHub Pages):** `./gradlew :app:web:wasmJsBrowserDistribution`, copy
     `app/web/build/dist/wasmJs/productionExecutable` to `<dir>/campfire`, copy the website repository's `404.html`
     to `<dir>`, and serve `<dir>` on `localhost:8080` with a small Python `http.server` subclass whose 404 answers
     with that `404.html` (status 404) and that serves `.wasm` as `application/wasm`. Open
     `http://localhost:8080/campfire/`.
  3. **Deployed:** `https://pandulapeter.com/campfire/` (the last release: a change that has not been released yet can
     only be tested on 1 and 2).
- **Sync** needs a build with `campfire.dropbox.appKey` in `local.properties` (the dev server and the local copy read
  it at build time). The web redirect URIs Dropbox knows are `https://pandulapeter.com/campfire/` and
  `http://localhost:8080/` — the local production copy at `/campfire/` is **not** registered, so do web sync tests on
  the dev server or the deployed site.
- **Looking at the library.** DevTools → Application → Storage shows usage; the files themselves are only reachable
  from the console. Songs:
  `for await (const [n,h] of (await (await (await navigator.storage.getDirectory()).getDirectoryHandle('library')).getDirectoryHandle('songs')).entries()) console.log(n,(await h.getFile()).size);`
  (replace `songs` with `setlists`; the root also holds `preferences`).
- **Starting clean:** DevTools → Application → Storage → "Clear site data" (this deletes the library — export first if
  it matters), then reload. A clean start plants the two demo songs and the demo setlist.
- **Fixtures** come from `fixtures/make_fixtures.py` (see `README.md`): refer to "fixture: …" below.
- Keep the DevTools console open during every test; **any red error or unhandled promise rejection is a finding**,
  even when the screen looks right.

---

## 1. Storage and data safety (OPFS)

- [ ] **WEB-001** (P0) A song written on the web survives a reload
  1. Clear site data, reload. Wait for the demo library.
  2. Create a song "Web save test" with a few chord lines; Save. Edit it again, change a word, Save.
  3. Reload the page (Cmd/Ctrl+R).
  4. Repeat in Chrome, Firefox and Safari 26.
  **Expected:** after the reload the song is there with the second edit. The console listing shows exactly one
  `.cho` for it and the demo files. No error in the console. DevTools → Sources → Threads shows **no**
  `opfs-writer.js` worker on these three browsers (they have `createWritable`).
  <sub>[r2-12][r3-01]</sub>

- [ ] **WEB-002** (P0) The worker fallback saves whole files on Safari 18.x
  1. In the Safari 18.x browser (see "Before you start"), open the dev server (or a local production copy on a Mac
     running macOS 15). Clear site data, reload.
  2. Confirm the demo library appears. Create a song, save it, then **edit it to be shorter** (delete half the text)
     and save; then make it **longer** again and save.
  3. Put a tag on it, reorder the demo setlist, switch the theme.
  4. Import fixture: the non-ASCII song (`Tükörfúrógép`) and fixture: the few-hundred-song zip.
  5. Reload.
  **Expected:** everything is there after the reload, byte for byte: the shortened version did not keep the tail of
  the longer one (open it in the editor and look at the end), the longer version is complete. The tag, the order and
  the theme survived. The OPFS root holds only `library` and `preferences`. One `opfs-writer.js` worker appears at the
  first write. Export the library and compare with the imported zip: the same files come back.
  <sub>[r2-12][r3-01][follow-up: OPFS partial write]</sub>

- [ ] **WEB-003** (P1) (dev patch) A write that stops short is reported, not taken for a save
  1. In `app/web/src/wasmJsMain/resources/opfs-writer.js`, make the handle's `write` return half the bytes it was given
     on its first call and 0 on the second (the follow-up's node reproduction does the same). Force the fallback in
     Chrome by adding `<script>delete FileSystemFileHandle.prototype.createWritable</script>` as the first script of
     `index.html`. Run the dev server.
  2. Open a song in the editor, change it, Save.
  **Expected:** the app says the song could not be saved (the "Could not save" snackbar); it does not claim success.
  Revert both patches afterwards. (Without the second 0, a short first write followed by the rest must simply save.)
  <sub>[follow-up: OPFS partial write]</sub>

- [ ] **WEB-004** (P1) (dev patch) The worker failing entirely is a failed save, not a broken app
  1. With the fallback forced as in WEB-003 (no other patch), block `opfs-writer.js` in DevTools → Network → "Block
     request URL".
  2. Save a song.
  3. Unblock and save again without reloading.
  **Expected:** step 2 shows "Could not save the song"; the web loading page's failure screen does **not** appear; no
  new OPFS entry. Step 3 saves normally.
  <sub>[r2-12]</sub>

- [ ] **WEB-005** (P1) A file that vanishes while the library is being listed is skipped, not an error
  1. Import fixture: the few-hundred-song zip. Reload so the song list fills in batches.
  2. While the list is still filling, open a song's menu and delete it.
  3. (With Dropbox connected on this tab, see WEB-050.) In another browser, delete 20 of those songs from dropbox.com,
     then press Sync now here with the Songs screen showing.
  **Expected:** no error state, no failure flash; the counts tick down; the console shows no `NotFoundError` and no
  unhandled rejection. The library is never reported as empty or unreadable.
  <sub>[r5-05]</sub>

- [ ] **WEB-006** (P0) A real storage failure is not mistaken for an empty library
  1. DevTools → Application → Storage → "Simulate custom storage quota", set just above current usage.
  2. Import fixture: the large-library zip until writes start failing.
  3. Edit an existing song, making it much longer, and save.
  **Expected:** failed writes are reported; **no zero-byte `.cho`** is left behind (console listing); the edited song
  keeps its old text if its save failed. Remove the quota simulation afterwards.
  <sub>[r2-12]</sub>

- [ ] **WEB-007** (P1) Delete and rename leave exactly one file
  1. Open a song, delete it from its menu while it is open.
  2. Rename another with "Update file name" (fixture: a song whose file name differs from its header).
  3. Reload.
  **Expected:** the deleted song is gone for good; the renamed song exists once under its new name only.
  <sub>[r1-33]</sub>

- [ ] **WEB-008** (P1) Storage persistence is reported in Settings
  1. Open Settings → Library in Chrome (not bookmarked, fresh profile if possible), then in Firefox, then in Safari.
  **Expected:** in place of a folder location, the row says either "Your browser has agreed to keep the library until
  you clear this site's data." or the "has not promised to keep the library… Export it or connect sync" sentence.
  Firefox may ask a permission question once — it must never be asked again on later loads.
  <sub>[CLAUDE][features]</sub>

## 2. Loading, the second tab, unsupported browsers

- [ ] **WEB-010** (P0) The app loads on every supported browser
  1. Open the local production copy (and later the deployed site) in Chrome, Firefox, Safari 26, Edge, iOS Safari, on
     a normal connection and once with DevTools throttling "Slow 4G".
  **Expected:** the loading page shows a determinate progress bar that moves steadily (not stuck at 0 and not jumping
  to the end), fades out into the app, and the app is fully drawn when it has faded (no half-rendered frame, no
  launch mark lingering). Hungarian browsers see Hungarian text.
  <sub>[r2-49][app/web]</sub>

- [ ] **WEB-011** (P1) A download that fails says so and can be retried
  1. DevTools → Network → block `campfire.js`, reload.
  2. Click "Try again" with it still blocked; unblock; click "Try again".
  3. Block the `.wasm` file instead and reload.
  **Expected:** 1: "Campfire could not be loaded. Check the connection and try again." with a Try again button. 2: the
  second attempt fails the same way; after unblocking it loads. 3: the bar stops with the same message rather than
  hanging.
  <sub>[r2-49][r1-69]</sub>

- [ ] **WEB-012** (P1) A second tab is told the app is already open
  1. Open the app in tab A. Open the same URL in tab B.
  2. In B click "Try again" while A is still open. Close A, click "Try again" in B.
  **Expected:** B shows the icon, the name, "Campfire is already open in another tab. Close that tab first, or carry
  on there." and a Try again button, **no progress bar**, and B's Network panel shows no `campfire.js` / `.wasm`
  request. After closing A, B downloads and starts with the library intact. With the browser in Hungarian the texts
  are Hungarian and `<html lang="hu">`.
  <sub>[r2-49][CLAUDE]</sub>

- [ ] **WEB-013** (P2) (dev patch) An unsupported browser is told before any download
  1. Change the first `WebAssembly.validate` probe in `index.html` so it returns false (or use Firefox < 120 / an iOS
     17 simulator if one is at hand). Reload.
  **Expected:** "This browser cannot run Campfire. It needs Chrome or Edge 119, Firefox 120, Safari 18.2 (iOS 18.2), or
  a later version." at once — no bar, no button, and neither `campfire.js` nor the `.wasm` is requested.
  <sub>[r2-53]</sub>

- [ ] **WEB-014** (P2) A drawable that never loads does not hold the app hostage
  1. Block `*ic_update.xml` (or another drawable) in DevTools, reload.
  **Expected:** the loading page leaves about 5 s after the bar ends; the app works; only that icon is missing.
  Without blocking, startup is as fast as usual.
  <sub>[r2-53]</sub>

## 3. Addresses, history and the browser's Back

- [ ] **WEB-020** (P1) Every screen has an address, and Back walks the app's own back stack
  1. On the dev server, visit in turn: Songs, the search, Setlists, the setlist search, each of the four Settings
     tabs, a song, its editor, a song opened from a setlist. Watch the address bar.
  2. Press the browser's Back repeatedly from the deepest screen.
  **Expected:** addresses are `/`, `search`, `setlists`, `setlists/search`, `settings/general|songs|library|about`,
  `song/<file>`, `song/<file>/edit`, `setlist/<setlist>/<song>`. Back goes one step at a time in the order a back
  gesture would, never skips a screen and never leaves the site before reaching Songs.
  <sub>[CLAUDE][presentation]</sub>

- [ ] **WEB-021** (P1) Back closes whatever is open on top first
  1. Open a dialog (new song), press browser Back. Open an overflow menu, Back. Open a bottom sheet (filters on a
     narrow window), Back. Open the search, Back.
  2. In the editor, type something unsaved, press browser Back.
  **Expected:** each Back closes only the thing on top and stays on the screen. In the editor the app's own unsaved
  changes question (save / discard / cancel) appears; Cancel keeps the text and the address.
  <sub>[CLAUDE][presentation]</sub>

- [ ] **WEB-022** (P1) A deep address opens the right screen, including through the 404 hand-off
  1. Local production copy: paste `http://localhost:8080/campfire/song/<an existing song file without .cho>` into a
     new tab (close the other tab first). Then `…/campfire/setlist/<setlist>/<song>`, `…/campfire/settings/about`,
     and `…/campfire/song/does_not_exist`.
  2. Repeat on the deployed site, including one address with `&` or `%` in a song name (fixture: `100% sure`).
  **Expected:** the launch screen waits and then opens exactly that screen, with Songs underneath it (Back leads
  there). An address naming nothing opens Songs. The whole path survives GitHub Pages' 404 page. A name with `&`/`%`
  round-trips.
  <sub>[CLAUDE][presentation][r4-49]</sub>

- [ ] **WEB-023** (P2) Forward restores what Back left, cut to what the library still holds
  1. Setlists → open a song in setlist `s` (songs a, b, c) → page to `b` → Back → Forward.
  2. Open `…/setlist/s/a`, Back ×2, Forward ×2. Open `…/song/a/edit`, Back, Back, Forward, Forward.
  3. Setlists → setlist song → Back → Settings from the rail → Forward.
  4. Remove `c` from `s`, Forward into the setlist pager; reorder to c, b, a, Forward; page to `b`, Back, remove `b`,
     Forward.
  **Expected:** 1: lands on `setlist/s/b` with Setlists under it; Back → Setlists. 2: Setlists, then the song in its
  setlist; song then its editor. 3: the setlist song over Setlists. 4: `a` with two pages; `a` as the third page;
  nothing opens and the app stays on Setlists.
  <sub>[r4-43][r4-44]</sub>

- [ ] **WEB-024** (P1) The browser guards unsaved editor text, and only then
  1. Type in the editor without saving. Press Cmd/Ctrl+R; choose Stay. Then close the tab; choose Stay. Then type
     another URL in the address bar.
  2. Save. Reload.
  3. In Chrome DevTools console: `getEventListeners(window).beforeunload` with and without unsaved text.
  **Expected:** the browser asks each time in 1 and Stay keeps the text; after Save no prompt. The listener exists
  only while there is unsaved text. Chrome, Firefox, Safari. (iOS Safari does not reliably ask — known limit.)
  <sub>[r2-13]</sub>

## 4. Keyboard

- [ ] **WEB-030** (P1) Ctrl/Cmd+S saves in the editor and never opens "Save page as"
  1. In the editor, press Ctrl/Cmd+S; hold it for two seconds.
  2. On the Songs screen press Ctrl/Cmd+S.
  3. On Windows/Edge with a Hungarian or Polish (AltGr) layout, type `@ { } [ ]` in the editor. With a Russian layout
     press Ctrl+S.
  **Expected:** 1: saved, no browser dialog, even held. 2: nothing happens (and no browser dialog either). 3: the
  characters type normally; Ctrl+S saves.
  <sub>[r3-20]</sub>

- [ ] **WEB-031** (P1) Ctrl/Cmd+F opens the app's search, and only takes the key when it did
  1. On Songs and on Setlists press Ctrl/Cmd+F; press it again with the search open.
  2. On a song's details screen (no search there) press Ctrl/Cmd+F.
  **Expected:** 1: the search opens with the caret in it; the second press selects the text; the browser's find bar
  never appears. 2: the browser's own find bar opens (the app leaves the key alone).
  <sub>[CLAUDE][presentation]</sub>

- [ ] **WEB-032** (P1) Escape closes one thing at a time, including dialogs with a focused field
  1. Open "New song" (its field has the caret), press Escape. Open a setlist edit dialog, Escape. Open the language
     picker, Escape.
  2. Hold Escape for two seconds on a song opened from a setlist.
  **Expected:** each Escape closes the dialog it was pressed in. A held Escape closes one thing only. Chrome and Safari.
  <sub>[r3-19][r3-20]</sub>

- [ ] **WEB-033** (P1) Modified arrow keys reach the browser on the song details screen
  1. Open a song from a setlist of three songs. Press Left / Right: the pager moves.
  2. Press Alt+Left (Windows/Linux) or Cmd+Left (Mac), and Alt/Cmd+Right after that.
  3. Press Ctrl+Up/Down and plain Up/Down.
  **Expected:** 1 pages songs. 2 is the browser's Back / Forward (the app navigates back to the previous screen and
  forward again), **not** a page turn. 3: plain Up/Down scroll the song; with a modifier the app leaves the key alone.
  <sub>[r5-34]</sub>

- [ ] **WEB-034** (P2) Text size follows Ctrl/Cmd + wheel and a pinch
  1. On a song, Ctrl/Cmd + scroll one notch at a time, with a mouse wheel and with a trackpad; pinch on the trackpad.
  **Expected:** one notch is one step, not a jump from end to end; the page does not zoom (the browser's zoom is not
  triggered).
  <sub>[r3-21]</sub>

## 5. Files in and out

- [ ] **WEB-040** (P0) Keep both keeps an imported setlist pointing at the numbered song
  1. Import fixture: the keep-both pair, part 1 (`song.cho` + a setlist naming it). Import part 2 (an edited
     `song.cho` + the same setlist file).
  2. Answer **Keep both**.
  3. Open the setlists.
  **Expected:** the library now has `song.cho` and `song_2.cho`; there are two setlists — the original still pointing
  at `song.cho`, and a numbered copy (`…_2.setlist.json`) whose entry opens the **edited** song. Repeat with
  **Replace** (one song, the edited text; one setlist) and **Skip** (one song, the old text; one setlist).
  <sub>[follow-up: import keep-both remap]</sub>

- [ ] **WEB-041** (P1) Drag and drop imports what it should
  1. Drag a folder containing `.cho` files, a `.DS_Store`, a `._song.cho` and a sub-folder onto the page (Chrome,
     Firefox, Safari).
  2. Drag a folder of 100+ songs. Drag two files and a folder at once. Drag an empty folder, then a single `.cho`.
  3. Drag text or a link from another tab onto the page.
  **Expected:** 1: the songs are imported; the hidden files and the sub-folder are not mentioned. 2: all arrive; one
  import for the mixed drop; the second drop after the empty folder still imports. 3: nothing happens, nothing logged.
  <sub>[r2-43]</sub>

- [ ] **WEB-042** (P1) The file picker does not lose a slow selection
  1. Import → pick 200 files from the picker (on iOS Safari: from Files, which is slow).
  2. Double-click Import. Cancel the picker, then click Import again.
  **Expected:** all 200 arrive; a double-click opens one dialog; after a cancel the next click opens the picker.
  <sub>[r1-67][r3-10]</sub>

- [ ] **WEB-043** (P1) Export downloads once per click and carries local dates
  1. Double-click Export in a song's menu, then ten times fast on a setlist's Export and on the library export.
  2. Open the downloaded zip in the OS and look at the entries' dates.
  **Expected:** one download per menu opening; zip entry times are the local time, not UTC.
  <sub>[r3-11][r2-31]</sub>

- [ ] **WEB-044** (P1) A library export of a large library is whole
  1. Import fixture: the large library (2,000+ songs) and a few setlists. Export the library.
  2. Unzip it and count the files.
  **Expected:** every song and setlist is in the archive, and the export's message does not mention skipped files.
  (Making one file unreadable is not practical in OPFS; the "names the files it could not read" half of this is
  tested on the desktop.)
  <sub>[r5-06]</sub>

- [ ] **WEB-045** (P1) Large imports stay responsive
  1. Import fixture: the 5,000-song songbook and its zip; a few hundred songs sharing one title; fixture: the ~20 MB
     file.
  **Expected:** the page stays responsive (the progress moves); the same-title songs are numbered `_2…_N` without
  gaps; the 20 MB file is reported as too large, not loaded.
  <sub>[r1-08][r2-26][r2-34][r2-15]</sub>

- [ ] **WEB-046** (P2) Tab columns line up in the bundled monospace font
  1. Open a demo song with tablature (House of the Rising Sun) on the dev server, then the local production copy.
  **Expected:** the staff lines' columns align (the web bundles JetBrains Mono); the font is there from the first
  paint, not swapped in later.
  <sub>[presentation][app/web]</sub>

- [ ] **WEB-047** (P1) Right-to-left lyrics read from the right, with their chords spread over them
  1. Import fixture: the Hebrew RTL song (four chords per line). Open it; narrow the window until a line wraps.
  **Expected:** the Hebrew lines start at the right edge; each chord sits over the word it belongs to (the first chord
  at the right, the last at the left); none stack on each other. After a wrap, chords follow their words onto the
  second line. The web shapes text with its own engine, so this is worth its own pass.
  <sub>[r5-35]</sub>

## 6. Sync — the parts that are the browser's

Run with a Dropbox account prepared as the README's "The Dropbox account" describes, on the dev server
(`http://localhost:8080/`) or the deployed site.

- [ ] **WEB-050** (P0) Connecting returns to the app, not to `index.html`
  1. Settings → Library → Connect Dropbox. Log in, Allow.
  2. After the return, reload with a stale `?code=abc&state=def` appended to the URL.
  **Expected:** Dropbox returns to `…/campfire/` (or `localhost:8080/`), the app opens on Settings → Library
  connected, and a first sync runs. The stale query string is removed and the tab stays connected.
  <sub>[r2-11][r1-27]</sub>

- [ ] **WEB-051** (P1) Going Back from the consent page does not leave the app stuck
  1. Connect, and on the Dropbox page press the browser's Back (Safari, Firefox; Chrome with the bfcache panel).
  2. With "Slow 3G", Connect and press Escape before the Dropbox page loads.
  **Expected:** restored from cache → "Waiting for the browser…" with Cancel; Cancel brings back Connect; Connect again
  completes. If not restored from cache, it reloads disconnected. In the console `navigator.locks.query()` shows the
  library lock held again after `pageshow`.
  <sub>[r2-21][r2-49]</sub>

- [ ] **WEB-052** (P1) Double-click Connect does not cancel the attempt
  **Expected:** one consent page, the attempt completes.
  <sub>[r3-13]</sub>

- [ ] **WEB-053** (P1) Offline behaviour
  1. DevTools → Offline. Sync now.
  2. Online again, reload.
  3. Put ~200 songs in the Dropbox folder (web UI), start a sync and go offline half-way.
  4. Offline, Disconnect.
  **Expected:** 1: "Could not reach the service. Campfire will try again next time." 2: no "interrupted" notice; the
  launch sync runs. 3: failure shown; Songs lists what arrived; the next run downloads only the rest. 4: the Connect
  button at once.
  <sub>[r3-05]</sub>

- [ ] **WEB-054** (P2) A storage failure during sync is named as one
  1. Simulated quota just below usage; Sync now.
  **Expected:** "The library could not be read or written." and a console log naming `sync-index.json` with
  `QuotaExceededError`.
  <sub>[r3-29]</sub>

- [ ] **WEB-055** (P0) Saving a song while a sync downloads it keeps the saved text
  1. Two tabs are not possible (Web Lock), so use dropbox.com in another browser as the other device. Sync a long song
     to the web. On dropbox.com, replace that song's content with a changed version.
  2. DevTools → Network → "Slow 3G". On the web app press Sync now, and while "Syncing…" shows, open that song's
     editor, change a word and Save.
  **Expected:** the word you saved stays in the song; the Dropbox version arrives next to it as `<name> (2).cho`
  (or, if the save landed before the download started, the run reports a kept-both conflict). Never: the saved word
  silently replaced by the Dropbox version.
  <sub>[r3-03][follow-up: sync save race]</sub>

- [ ] **WEB-056** (P2) A first sync of 2,000+ songs keeps the page responsive
  **Expected:** the progress row moves, the song list grows during the run, scrolling stays smooth.
  <sub>[r2-23]</sub>
