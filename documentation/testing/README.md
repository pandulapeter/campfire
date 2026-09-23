<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Manual test scripts

This folder is Campfire's full regression suite: every check that the unit tests cannot make, because it needs a
real device, a real window, a real Dropbox account or a real release. The unit tests cover `:chordpro`, the import
planner, the zip and file storage, and the sync planner and engine; everything else is here. The documents are
written for a tester who knows Campfire as a user but has not read its code, and they describe the app as it
should behave, so they can be run against any build.

**Keeping them true is part of every change.** A change to what the user sees or what happens to their files updates
the test that describes it, or adds one, in the same commit. A test that names a behaviour the app no longer has is
a bug in this folder: fix the test, not the app — unless the behaviour went away by mistake, which is exactly what
the suite is here to catch.

## Which document on which device

| Device | Documents | Notes |
| --- | --- | --- |
| Mac | [00 Core functional](00-core-functional.md), [03 macOS](03-macos.md), [06 Web](06-web.md) in Safari and Chrome, the simulator parts of [02 iOS](02-ios.md) | The desktop build is the fastest loop: run 00 here. |
| iPhone | [02 iOS](02-ios.md) | Files app, iCloud Drive, share sheet, VoiceOver, reinstall. |
| Android phone | [01 Android](01-android.md) | Rotation, process death, TalkBack, open with, backup. Some checks want API 29/30 as well as 34+ (emulator). |
| Windows PC | [04 Windows](04-windows.md), [06 Web](06-web.md) in Edge, Chrome and Firefox | MSI install, antivirus, foreign file names, Narrator. |
| Linux (optional) | [05 Linux](05-linux.md) | A VM or a live USB of Ubuntu 22.04 **and** 24.04: the `.deb` has to install on both. |
| Two or more of the above, plus a Dropbox account (see below) | [07 Sync, multi-device](07-sync-multi-device.md) | The riskiest document: the deletion guard is the one change that can destroy a library if it is wrong. |
| Release day | [08 Release and stores](08-release-and-stores.md) | Workflows, signing, store submissions, Play's update priority. |

## Running a regression

A full regression is every document, on every device in the table above. Before a release, run at least the
smoke test of each platform document and every P0; after a change to one area, run that area's tests in every
document that has them.

**Don't tick the boxes in these files.** They are the suite, not a record of one pass through it. Keep each run's
results in a run log outside the repository (a copy of the documents, a spreadsheet, or a GitHub issue per run) that
names, at the top:

- the build: `git rev-parse --short HEAD` or the release tag, and the version Settings → About shows;
- the date, and who (or what) ran it;
- every device, with its OS version, and every browser, with its version.

Then one line per test: its ID, pass / fail / skipped, and for a skip the reason ("no Windows PC").

### Recommended order

1. **00 Core functional on the macOS desktop build.** Most behaviour is shared code, and the desktop is where a
   file can be put into the library folder and checked afterwards with a text editor or `xxd`.
2. **Every P0 in every other document**, starting with 07 (sync) and 04 (Windows). A P0 failure is a release blocker.
3. The P1s, then the P2s, one device at a time.

Priorities:

- **P0** can lose or corrupt the user's library, or blocks a release or a store submission.
- **P1** is visible breakage: a crash, a wrong result, a dead control, text that cannot be read.
- **P2** is polish.

## Format

Every document starts with **Before you start**, which covers the build to install and how, the fixtures to load, a
rough time, and what to write down. The tests follow, grouped by area and ordered by risk within each group:

```
- [ ] **CORE-042** (P1) Title
  1. Step.
  2. Step.
  **Expected:** what must happen.
  <sub>r5-07</sub>
```

The ID prefix names the document: `CORE`, `AND`, `IOS`, `MAC`, `WIN`, `LNX`, `WEB`, `SYNC`, `REL`. The small tag at the
end names where the check came from, which is where to read why the behaviour is the way it is. `r3-17` is plan 17
of the third pre-release review; each review's plans can be read with
`git show <commit>:documentation/issues/<file>` (`git ls-tree <commit> documentation/issues/` lists them):

| Review | Commit |
| --- | --- |
| r1 | `d40f9754` |
| r2 | `8ab3c909` |
| r3 | `693f567b` |
| r4 | `f5d5c071` |
| r5 | `a8597f89` |

The other tags are `features`, `sync.md`, `file-format` (the `documentation/*.md` files), `CLAUDE` (the root
`CLAUDE.md`), and module names for the module `CLAUDE.md` files. A test added later carries the tag of whatever
asked for it in the same way: a review plan, a GitHub issue (`#123`) or the document that states the rule.

Behaviour that is the same everywhere is tested once, in 00. The device documents test what differs on that
platform. Each also opens with a **10-minute smoke test** to run first on every new build of that platform.

## Fixtures

`fixtures/make_fixtures.py` writes every test file these documents mention. It needs Python 3.8 or later and no
other packages. Run it **outside the repository** (for example in Downloads), so nothing generated ends up
committed:

```
cd ~/Downloads
python3 /path/to/campfire/documentation/testing/fixtures/make_fixtures.py               # about 30 MB
python3 /path/to/campfire/documentation/testing/fixtures/make_fixtures.py --large 2000 50  # also a large library
```

This writes `campfire-fixtures/` and `campfire-fixtures.zip`. To get the files onto a phone, AirDrop the zip, mail
it, or push it with `adb push campfire-fixtures /sdcard/Download/`, then unzip it in the Files app. Don't import
the zip itself into Campfire: it is a bundle of fixtures, not a library archive.

| File | What it is for |
| --- | --- |
| `songs/rtl-hebrew.cho`, `songs/rtl-arabic.cho` | Right-to-left lyrics with four chords on a line; the Arabic one wraps on a narrow window. |
| `songs/bass-notes.cho` | `[D/f#] [d/f#] [A/c#] [Am7/G]`: a lowercase bass note follows its root and keeps its case. |
| `songs/german-bass.cho`, `songs/german-notation.cho` | German notation (`H`), `[C/h]`. |
| `songs/lowercase-minors.cho` | `[a] [e] [h]` read as minor chords. |
| `songs/bracket-labels.cho` | `[Intro] [Break] [Chorus 2x] [Bass] [Ebony]` (never transposed), `[ *softly]`, `[]`, `[ G ]`. |
| `songs/nbsp-tab-grid.cho` | Non-breaking spaces inside a tab and a grid. |
| `songs/wide-tab-multibyte.cho` | A tab row that wraps, holding emoji, `𝄞` and a decomposed `é`. |
| `songs/cr-only.cho`, `songs/crlf.cho` | Old Mac (CR only) and Windows (CRLF) line endings. |
| `songs/bom-start.cho`, `songs/bom-double.cho`, `songs/bom-joined.cho` | Byte order marks: at the start, doubled, and in the middle of two files joined into one. |
| `songs/cp1250-hungarian.cho`, `songs/cp1252-french.cho`, `songs/utf16-with-bom.cho`, `songs/utf16-no-bom.cho` | Legacy encodings. |
| `songs/nfc/*`, `songs/nfd/*` | The same Cyrillic, accented Latin and Vietnamese titles composed (NFC) and decomposed (NFD). |
| `songs/every-directive.cho` | Every directive the song details screen draws, both languages, a chorus recall, a tab, a grid, an ABC block. |
| `songs/sort-*.cho` | Titles and artists that start with digits, punctuation and non-Latin letters; `Y.M.C.A.`; `Gęsi za wodą`. |
| `songs/percent-100-sure.cho` | `%` and `$` and `\` in titles, tags and header values. |
| `songs/colonless-directives.cho`, `songs/meta-directives.cho`, `songs/unicode-accidentals.cho` | Directive spellings other apps write, and `♯ ♭` accidentals. |
| `songs/tall-section.cho`, `songs/crafted-comment.cho` | Hostile input: a 6,000-line verse and a very long comment and chord name. |
| `songs/hidden/._test.cho`, `songs/hidden/.DS_Store` | macOS litter that must never become a song. |
| `import/library-archive.zip` | Two songs and a setlist, as an export would contain them. |
| `import/keep-both-1.zip`, `import/keep-both-2.zip` | The "Keep both" import: the second archive has the same song edited, and the setlist unchanged. |
| `import/setlist-missing-song.setlist.json` | A setlist that names a song the library does not have. |
| `import/setlist-future-field.setlist.json` | Fields a later version might write (`venue`, and `note` on an entry) that must survive a save. |
| `import/windows-oem-names.zip` | An archive made with Windows' "Send to → Compressed folder", with an accented entry name. |
| `import/finder-with-junk.zip` | Songs in a subfolder, with `__MACOSX`, a PDF and an MP3 that must be skipped. |
| `import/songbook.txt` | Three songs split by `{new_song}`, one without a title. |
| `size/oversized-9mib.cho`, `size/huge-20mb.cho` | Over the 8 MiB limit for a single file. |
| `dropbox-only/*` | Names Windows cannot store. Upload them to the Dropbox folder from dropbox.com, never through Campfire. |
| `large-library/`, `large-library.zip` | Written only with `--large`: generated songs in 12 tag groups and two languages, plus setlists. |

## The Dropbox account

Sync only exists in builds that have `campfire.dropbox.appKey` in `local.properties`; a build without it says so in
Settings → Library. Campfire's Dropbox app is registered for the *app folder* permission, so everything it and the
tests touch is `Apps/Campfire Sync/` (`songs/` and `setlists/` inside it) and nothing else in the account.

The deletion-guard tests in 07 empty that folder and answer "delete them here too" on purpose, so either:

- use an **account made for testing**, or
- use a **real account, and snapshot the folder first**. Every real installation connected to it has to be
  disconnected before the run (Settings → Library → Disconnect, which removes nothing), and none of them may be
  opened until the folder is restored: the tests would reach their libraries. Then:
  1. Connect a desktop installation made for the test (see "Isolated desktop installations" below) with an **empty**
     library. Its first run downloads the whole folder, and its `library/` folder is the snapshot. Copy it aside.
  2. After the run, restore with `tools/dropbox_folder.py --credentials <that installation>/preferences/sync-credentials.json restore <snapshot>`,
     which makes the folder hold exactly the snapshot and checks every file against Dropbox's content hash.
  3. Disconnect every test installation, and reconnect the real ones. A reconnected installation that was
     *disconnected* compares by content, so it moves nothing that is the same. One that was only *refused* (SYNC-003
     revokes the app for the whole account) keeps its index and also finds nothing to do.

Either way, check at the start of a session that the app folder holds only `songs/` and `setlists/`.

`tools/dropbox_folder.py` is also what the sync tests mean by "via the API": it lists, uploads, deletes, empties a
folder and rewrites one file in a loop, borrowing the refresh token of a test installation. `--help` lists its
commands.

## Running the tests from a script

Much of 00, 06 and 07 can be run on one Mac without a person at the keyboard, by Claude or by anyone scripting it:

- **Isolated desktop installations.** The desktop app derives its data folder and its single-instance lock from
  `user.home`, so `JAVA_TOOL_OPTIONS=-Duser.home=<dir> …/Campfire.app/Contents/MacOS/Campfire` (after
  `./gradlew :app:desktop:createDistributable`) is an installation of its own, and several of them are several
  devices. Their libraries and `preferences/sync-index.json` are plain files to seed and read. Connect them one
  after the other: the desktop's sign-in redirect uses the fixed port 53682.
- **Pressing things.** Synthetic clicks are unreliable against a Compose window. What works is a temporary driver in
  `app/desktop` (a `LaunchedEffect` next to where the view model is obtained) that reads commands from a file in the
  installation's home — sync with a given deletion policy, stop, save, delete, connect, disconnect — and logs every
  `syncState` it sees. To open the consent page in a browser tab of your choosing, have the desktop authenticator
  write the authorization URL to a file instead of opening the default browser. Both are test-only edits: never
  commit them.
- **A slow network**, where a test needs a run to still be going: macOS Network Link Conditioner, or a temporary
  delay in `SyncEngine` before the transfer of files whose name contains a marker (for example `slowtest`).
- **The web build** in a Chrome tab, its OPFS library read and written from the console (see `06-web.md`); the
  **Android emulator** (`.debug` build, `adb`); and the **iOS simulator** (`simctl`). A Play system image has no
  root, so the Android library is changed through the app itself there.

These always need a person:

- a **real iPhone**: iCloud Drive files opened in place, AirDrop and Mail, a reinstall that keeps its Keychain
  items, Dynamic Type, VoiceOver;
- a **Windows PC**: the MSI, an antivirus scanning the library, Narrator, the Microsoft Store listing;
- a **real macOS logout or restart** with the app open and unsaved text, and changing the system clock;
- **TalkBack**, **VoiceOver** and **Narrator** in general, a hardware page-turner pedal, and the on-screen keyboards;
- **logging in** to Dropbox on a consent page, and **revoking** the app in the account's settings;
- anything behind a **store account**: the Play internal track and its update priority, TestFlight, and App Store
  Connect.

## Reporting a failure

For each failure, write down:

1. **The test ID**, and the step where it went wrong.
2. **The device**: model, OS and version, browser and version for the web.
3. **The build**: `git rev-parse --short HEAD` of the checkout it was built from, or the release tag. Settings →
   About shows the version.
4. **What happened**, against the **Expected** line. Add a screenshot or a short recording.
5. **The files involved**: the song or setlist file, or the whole library folder zipped. Settings → Library shows
   where it is; on the web, use Export library. For sync, add the Dropbox folder's state and the time of the run.

Add it as a GitHub issue, and put its link next to the test's line in the run log.
