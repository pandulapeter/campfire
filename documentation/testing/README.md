<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Release testing

This folder is what is run by hand, or by a script on a Mac, before a Campfire release: the
**[release check](release-check.md)**, half an hour of the checks whose failure would block one — a build that does
not start, a library that is damaged, a sync that deletes what it should not. Everything else is covered by the unit
tests (see the root `CLAUDE.md`) or not at all.

**Keeping it true is part of every change.** A change to what the release check exercises — the first run, importing,
sync, the packaged builds — updates the check in the same commit. A check that names a behaviour the app no longer
has is a bug in this folder: fix the check, not the app, unless the behaviour went away by mistake.

## Recording a run

Don't tick the boxes in `release-check.md`: it is the check, not a record of one pass through it. Keep each run's
results outside the repository (a GitHub issue per release works well), starting with the build
(`git rev-parse --short HEAD` or the release tag, and the version Settings → About shows), the date, who or what ran
it, and every device and OS version. Then one line per check: its ID, pass / fail / skipped, and the reason for a
skip. A failure gets its own GitHub issue with the steps, what happened against the **Expected** line, a screenshot,
and the files involved (the song or setlist file, or the library folder zipped; for sync, the state of the Dropbox
folder and the time of the run).

## The Dropbox account

Sync only exists in builds that have `campfire.dropbox.appKey` in `local.properties`; a build without it says so in
Settings → Library. Campfire's Dropbox app is registered for the *app folder* permission, so everything it and the
tests touch is `Apps/Campfire Sync/` (`songs/` and `setlists/` inside it) and nothing else in the account.

The sync checks empty that folder on purpose, so either:

- use an **account made for testing**, or
- use a **real account, and snapshot the folder first**. Every real installation connected to it has to be
  disconnected before the run (Settings → Library → Disconnect, which removes nothing), and none of them may be
  opened until the folder is restored: the tests would reach their libraries. Then:
  1. Connect a desktop installation made for the test (see "Isolated desktop installations" below) with an **empty**
     library. Its first run downloads the whole folder, and its `library/` folder is the snapshot. Copy it aside.
  2. After the run, restore with `tools/dropbox_folder.py --credentials <that installation>/preferences/sync-credentials.json restore <snapshot>`,
     which makes the folder hold exactly the snapshot and checks every file against Dropbox's content hash.
  3. Disconnect every test installation, and reconnect the real ones. A reconnected installation that was
     *disconnected* compares by content, so it moves nothing that is the same.

Either way, check at the start of a session that the app folder holds only `songs/` and `setlists/`.

`tools/dropbox_folder.py` is also how the checks change the cloud folder behind the app's back: it lists, uploads,
deletes and empties, borrowing the refresh token of a test installation. `--help` lists its commands.

## Running the tests from a script

The release check is written to run on one Mac without a person at the keyboard, by Claude or by anyone scripting
it:

- **Isolated desktop installations.** The desktop app derives its data folder and its single-instance lock from
  `user.home`, so `JAVA_TOOL_OPTIONS=-Duser.home=<dir> …/Campfire.app/Contents/MacOS/Campfire` (after
  `./gradlew :app:desktop:createDistributable`) is an installation of its own, and several of them are several
  devices. Their libraries and `preferences/sync-index.json` are plain files to seed and read. Connect them one
  after the other: the desktop's sign-in redirect uses the fixed port 53682.
- **Pressing things.** Synthetic clicks are unreliable against a Compose window. What works is a temporary driver in
  `app/desktop` (a `LaunchedEffect` next to where the view model is obtained) that reads commands from a file in the
  installation's home — sync with a given deletion policy, stop, save, delete, connect, disconnect — and logs every
  `syncState` it sees. To open the consent page in a browser tab of your choosing, have the desktop authenticator
  write the authorization URL to a file instead of opening the default browser. `tools/desktop-test-driver.patch`
  is both (`git apply` it, and `git apply -R` it afterwards); they are test-only edits, never to be committed.
- **The web build** in a Chrome tab. A tab the automation cannot bring forward is hidden, and Compose draws nothing
  in a hidden tab: right after every page load, run
  `window.requestAnimationFrame = cb => setTimeout(() => cb(performance.now()), 16)` in it. The library is read from
  the console: `(await navigator.storage.getDirectory()).getDirectoryHandle('library')`.
- The **Android emulator** (`adb`) and the **iOS simulator** (`simctl`).

What always needs a person: logging in to Dropbox on a consent page, and the Windows PC.
