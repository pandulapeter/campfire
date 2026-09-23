<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Release check

The half-hour gate before a release: only the failures that would block one — a build that does not start, a
library that is damaged, a sync that deletes what it should not — cut down to what one Mac can show without a person
at the keyboard. How to record a run, prepare the Dropbox account and drive the app from a script is in the
[README](README.md).

**Who runs it:** Claude (or anyone scripting it) on a Mac. **Your part:** say go, and log in to Dropbox in Chrome if
it asks. On a Windows PC, section 5 takes 10 minutes by hand.

**Time:** about 10 minutes of building (unattended), 15 of checking, 5 of restoring the Dropbox folder when a real
account is used.

## 1. Build and unit tests (unattended)

- [ ] **RC-01** Every unit test passes: the command in the root `CLAUDE.md`.
- [ ] **RC-02** Every release artifact builds: `:app:android:assembleRelease`, `:app:desktop:createReleaseDistributable`,
  `:app:web:wasmJsBrowserDistribution`, and the iOS app for the simulator:
  `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -configuration Release -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`.
  All with the Dropbox key in `local.properties`.

## 2. Every build starts on an empty library

Each one: a clean install or data folder, launched once. **Expected:** the two demo songs and the demo setlist are
in its library within a few seconds, the process is still running after 20 seconds, and its log names no exception.

- [ ] **RC-03** Desktop, the ProGuard'd release image, with `user.home` pointing at an empty folder.
- [ ] **RC-04** Android, the release APK on the emulator after `adb uninstall`: no `FATAL` in `adb logcat`. The
  release build is not debuggable, so the library itself is checked on the debug build in the same run.
- [ ] **RC-05** iOS, the Release build on a simulator after `simctl uninstall`: the demo files are in the app
  container's `Documents/library`.
- [ ] **RC-06** Web, the production distribution in Chrome with its site data cleared: serve
  `app/web/build/dist/wasmJs/productionExecutable` on `localhost:8080` with any static server that sends `.wasm` as
  `application/wasm` (`python3 -m http.server 8080` from that folder; check with `curl -I`) and open it. The demo
  songs are in OPFS and the console has no errors.

## 3. The library keeps what it is given (desktop release image)

Files are handed to the running app as arguments (`…/Campfire <file>`), which is how "open with" reaches it.

- [ ] **RC-07** Import three files written in a scratch folder:
  `printf '{title: Old Mac}\r{artist: Test}\r[C]Line one\r[G]Line two\r' > cr.cho`,
  `printf '\xef\xbb\xbf{title: With BOM}\n{artist: Test}\n[Am]Words\n' > bom.cho` and
  `printf '{title: Plain}\n{artist: Test}\n{tag: Demo}\n[D]Words\n' > plain.cho`. **Expected:** each lands in
  `library/songs` under a name built from its header (`test-old_mac.cho`, …); the first keeps its CR line endings
  and the second has no byte order mark (`xxd`).
- [ ] **RC-08** Import the same three again. **Expected:** nothing new in the library — identical files are
  disregarded, never numbered or overwritten.
- [ ] **RC-09** Quit, and start again. **Expected:** the library is exactly as it was, the preferences are kept, and
  the demo songs are not planted a second time.

## 4. Sync (two desktop installations, one Dropbox folder)

Apply `tools/desktop-test-driver.patch`, build with `:app:desktop:createDistributable`, and prepare the account as
the README's "The Dropbox account" says — with a real account, installation A's first run is the snapshot. Revert
the patch at the end.

- [ ] **RC-10** Connect installation A (empty library), then B (the demo library). **Expected:** both show the account;
  B's demo songs go up once and A receives them; a second run on each moves nothing.
- [ ] **RC-11** Edit a song on A, sync A, sync B. Then edit one song differently on both, sync A, sync B, sync A.
  **Expected:** the first edit arrives on B; the second leaves `x.cho` and `x (2).cho` on both sides, and nothing is
  lost.
- [ ] **RC-12** Delete a song on A and sync A; before syncing B, edit that song on B; sync B, then A. **Expected:** the
  song is back everywhere with B's edit.
- [ ] **RC-13** Empty `songs/` in the cloud folder (`tools/dropbox_folder.py … empty /songs`) and sync A. **Expected:**
  the run stops and asks, and A's library is untouched. Answer *Keep them and upload*: the folder is full again and
  B's next run moves nothing.
- [ ] **RC-14** Quit A, move its `library/songs` folder away, start it. **Expected:** the launch run stops and asks
  about deleting from the cloud, and the cloud folder is untouched. Answer *Keep them and download*: the songs come
  back.
- [ ] **RC-15** Start a run on A with at least a dozen files to move and `kill -9` it halfway; start A again.
  **Expected:** it reports the last sync as interrupted and starts no run; *Sync now* finishes it with no copies.
- [ ] **RC-16** Disconnect A and B. **Expected:** both libraries and the cloud folder keep their files. Then restore
  the folder from the snapshot (`tools/dropbox_folder.py … restore`) if a real account was used.

## 5. Windows (10 minutes, by hand)

On the PC, with the MSI the release will attach:

- [ ] **RC-17** Install it over the previous version, and start it from the Start menu. **Expected:** the library is
  the one that was there, and a first install plants the demo library.
- [ ] **RC-18** Open a song in Notepad and keep it open; edit the same song in Campfire and save. Then save in Notepad
  and bring Campfire forward. **Expected:** neither save fails, and Campfire shows Notepad's text.
- [ ] **RC-19** Double-click a `.cho` file in Explorer while Campfire is running. **Expected:** the running window
  comes forward with the song imported and open; no second window.

Not covered: real phones and their backups, screen readers, a real macOS logout, Linux packages, the store tracks
and the release workflows themselves. A release that changes one of those is tested there by hand.
