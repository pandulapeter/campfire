<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Manual tests: macOS desktop

These tests cover what is specific to the desktop build on a Mac: the window, the keyboard, quitting (a real logout
included), "open with", the single-instance lock, drag and drop, the library folder that Finder can reach, and the
packaged runtime that ProGuard and jlink produce. Anything that behaves the same on every platform is in
`00-core-functional.md`. Run that file once on the Mac too if it is your main machine.

## Before you start

- **Two artifacts, both needed.**
  - `./gradlew :app:desktop:run` is the quick loop for the keyboard and window tests.
  - The **packaged release** is what ships. ProGuard and the jlink runtime only break that one: a `VerifyError`, a
    missing JDK module, or language names shown in English. Build it with
    `./gradlew :app:desktop:createReleaseDistributable`, which writes
    `app/desktop/build/compose/binaries/main-release/app/Campfire.app`. Build the installer with
    `./gradlew :app:desktop:packageReleaseDmg`.
  - Also get the unsigned `.dmg` attached to the latest GitHub release (the `desktop-publish.yml` output). That is
    what a stranger downloads.
- **Distribution flag.** A default build is `campfire.desktop.distribution=download`. The Mac App Store variant is
  `-Pcampfire.desktop.distribution=mac-app-store`. The allowed values are `download`, `mac-app-store`,
  `microsoft-store` and `linux`.
- **Library folder:** `~/Library/Application Support/Campfire` (`library/songs`, `library/setlists`,
  `preferences/`, `instance.lock`, `instance.endpoint`). To start with an empty library, rename that folder and
  rename it back afterwards. **Back up your real library before this session.**
- **Fixtures:** run `documentation/testing/fixtures/make_fixtures.py` once. The tests below refer to its files as
  "fixture: …".
- **A throwaway macOS user account** for the logout and restart tests (MAC-010 to MAC-014), so a failed test cannot
  interrupt your real session.
- **Time:** about 2.5 hours. The smoke section takes 10 minutes.
- **Record** the build (commit or release tag), the macOS version and the chip (Apple silicon or Intel). For any
  failure, also record a screenshot and the terminal output of the packaged binary started from a shell
  (`…/Campfire.app/Contents/MacOS/Campfire`).

Legend: **P0** can lose or corrupt the library, or blocks the release. **P1** is visible breakage. **P2** is polish.

## Smoke (10 minutes, packaged build)

- [ ] **MAC-S01** (P0) The packaged release starts at all
  1. Move `~/Library/Application Support/Campfire` aside.
  2. Start `…/main-release/app/Campfire.app/Contents/MacOS/Campfire` from Terminal.
  3. Wait for the song list.

  **Expected:** the launch mark fades into the Songs screen, the two demo songs and the demo setlist are present, and
  the terminal shows no exception (in particular no `VerifyError` or `NoClassDefFoundError`). The process keeps
  running.
  <sub>[app/desktop][CLAUDE]</sub>
- [ ] **MAC-S02** (P0) Open, edit, save, reopen
  1. Open a demo song, then the editor.
  2. Add a word, then press Cmd+S.
  3. Quit with Cmd+Q and start the app again.

  **Expected:** the word is still there. The `.cho` file in `library/songs` contains it.
  <sub>[features]</sub>
- [ ] **MAC-S03** (P1) Settings tabs render
  1. Open all four Settings tabs.

  **Expected:** General, Songs, Library and About all render. Library shows the folder path under Location.
  <sub>[presentation]</sub>
- [ ] **MAC-S04** (P1) Open With from Finder
  1. With the app running, right-click fixture: all-directives song in Finder.
  2. Choose Open With → Campfire.

  **Expected:** the window comes forward, the song is imported and opened.
  <sub>[r2-44]</sub>

## 1. Quitting and unsaved text (real logout included)

The quit request macOS sends to an app (Cmd+Q, the app menu's Quit, logout, restart and shutdown) is now *answered*
rather than cancelled up front.

- [ ] **MAC-001** (P0) Cmd+Q with nothing unsaved
  1. Press Cmd+Q on the Songs screen.

  **Expected:** the app quits at once, with no dialog.
  <sub>[r5-26]</sub>
- [ ] **MAC-002** (P0) Cmd+Q with unsaved editor text, answered with Save
  1. Type into the editor without saving.
  2. Press Cmd+Q, then choose Save.

  **Expected:** the unsaved-changes question appears. After Save the file is written and then the app quits. On the
  next start the text is there.
  <sub>[r5-26][r1-54]</sub>
- [ ] **MAC-003** (P0) Cmd+Q, answered with Discard
  1. Repeat MAC-002, but choose Discard.

  **Expected:** the app quits and the file is unchanged.
  <sub>[r5-26]</sub>
- [ ] **MAC-004** (P1) Cmd+Q, answered with Cancel
  1. Repeat MAC-002, but choose Cancel.

  **Expected:** the app stays open with the editor and the text as they were. A second Cmd+Q asks again.
  <sub>[r5-26]</sub>
- [ ] **MAC-005** (P1) The app menu's Quit behaves like Cmd+Q
  1. Repeat MAC-002 using Campfire → Quit Campfire.

  **Expected:** the same question, with the same three outcomes.
  <sub>[r5-26]</sub>
- [ ] **MAC-006** (P1) The window's close button with unsaved text
  1. Type into the editor without saving.
  2. Click the red close button and try each answer in turn.

  **Expected:** Save writes the file and then closes; Discard closes; Cancel keeps the window.
  <sub>[r1-54][r2-55]</sub>
- [ ] **MAC-007** (P1) A save failure stops the quit
  1. Make `library/songs` read-only with `chmod a-w`.
  2. Edit a song, press Cmd+Q and choose Save.

  **Expected:** the dialog closes, the editor keeps the text, the "save failed" message is shown, and the app does
  **not** quit. Restore the folder with `chmod u+w`.
  <sub>[presentation][r2-05]</sub>
- [ ] **MAC-008** (P1) Stay after a close, then go back and discard
  1. Type into the editor, close the window, and choose to stay.
  2. Press Back, then choose Discard.

  **Expected:** the editor closes and the app does **not** exit, because the earlier exit request was dropped.
  <sub>[r2-55]</sub>
- [ ] **MAC-009** (P1) The import question gives way to the unsaved-changes question
  1. Leave unsaved text in the editor.
  2. Import a file that collides, so the conflicts question is up.
  3. Close the window.

  **Expected:** the unsaved-changes question replaces the import question. Choose to stay, then import again: the
  import asks again.
  <sub>[r2-55]</sub>
- [ ] **MAC-010** (P0) Log out with nothing unsaved (throwaway account)
  1. With Campfire running, choose Apple menu → Log Out.

  **Expected:** the logout completes, and no "Campfire interrupted log out" alert appears.
  <sub>[r5-26]</sub>
- [ ] **MAC-011** (P0) Log out with unsaved text, answered with Save
  1. Leave unsaved text in the editor.
  2. Choose Apple menu → Log Out.
  3. Choose Save in Campfire's question.

  **Expected:** Campfire's question appears over the app and the logout waits for it. After Save the file is written,
  Campfire quits and the logout continues on its own.
  <sub>[r5-26]</sub>
- [ ] **MAC-012** (P1) Log out with unsaved text, answered with Cancel
  1. Leave unsaved text in the editor.
  2. Choose Log Out, then Cancel in Campfire's question.

  **Expected:** the logout stops, with macOS naming Campfire as the app that interrupted it. Campfire stays open with
  the text. Logging out again afterwards works normally.
  <sub>[r5-26]</sub>
- [ ] **MAC-013** (P1) Restart cancelled from the system dialog
  1. Choose Apple menu → Restart…, then Cancel in the system's own confirmation.
  2. Choose Log Out.

  **Expected:** the logout goes through, with no stale "interrupted" state left behind.
  <sub>[r5-26]</sub>
- [ ] **MAC-014** (P1) Quitting after handing over to a second process
  1. Start the app.
  2. Start a second copy with a song (`open -n …/Campfire.app --args <file>`). It hands over and exits.
  3. Leave unsaved text in the first copy, press Cmd+Q and choose Save.
  4. Start the app again.

  **Expected:** the text is saved, and the new start opens normally.
  <sub>[r5-26]</sub>

## 2. Keyboard

- [ ] **MAC-020** (P1) Modified arrow keys on song details
  1. Open a song from a setlist with at least three songs.
  2. Press plain Up/Down, then plain Left/Right.
  3. Press Cmd+Left, Cmd+Right, Alt+Left, Alt+Right and Ctrl+Left.

  **Expected:** plain Up/Down scroll smoothly and plain Left/Right step through the setlist. None of the modified
  combinations pages or scrolls the song: they are left to the system. Plain Left/Right do nothing at either end of
  the setlist.
  <sub>[r5-34]</sub>
- [ ] **MAC-021** (P1) Escape closes one layer at a time
  1. Open a song from Setlists.
  2. Open a sheet or dialog, then press Escape repeatedly.
  3. On the Songs screen, press Escape.

  **Expected:** each press closes the dialog, then the search, then the song, then goes back one screen. On the root
  screen Escape quits (asking first if there is unsaved text).
  <sub>[r3-19][app/desktop]</sub>
- [ ] **MAC-022** (P1) A held Escape counts once
  1. Hold Escape for 2 seconds on a song opened from Setlists.

  **Expected:** only the song closes. Also try holding Escape:
  - on a sheet, and on a delete confirmation: only that closes;
  - in the song search: the search closes once;
  - in the editor with unsaved text: the question opens once and stays.
  <sub>[r3-19]</sub>
- [ ] **MAC-023** (P2) Escape still works after switching apps
  1. Hold Escape, Cmd+Tab away and release Escape.
  2. Come back to Campfire and press Escape.

  **Expected:** the press works as usual.
  <sub>[r3-19]</sub>
- [ ] **MAC-024** (P1) Cmd+F opens the search
  1. Press Cmd+F on Songs, then on Setlists.
  2. Type something, click elsewhere, and press Cmd+F again.
  3. Press Cmd+F with a dialog, a sheet or a menu open.

  **Expected:** the screen's own search opens, and the second Cmd+F selects the typed text. Nothing happens under the
  dialog, sheet or menu.
  <sub>[presentation]</sub>
- [ ] **MAC-025** (P1) Cmd+S in the editor
  1. Press Cmd+S in the editor.

  **Expected:** the song is saved, the Save button disables and no system dialog appears.
  <sub>[presentation]</sub>
- [ ] **MAC-026** (P2) Accented letters on a Hungarian keyboard
  1. With the Hungarian input source active, type `ő ű á` in the editor and in the search.

  **Expected:** the letters are typed and no shortcut fires.
  <sub>[r3-20]</sub>
- [ ] **MAC-027** (P2) Ctrl or Cmd with the scroll wheel changes the text size
  1. Use Cmd+scroll (a trackpad pinch, or a wheel) on song details.

  **Expected:** the text size changes in steps, is not jumpy, and is remembered.
  <sub>[presentation]</sub>

## 3. Open with, the single instance and drag and drop

- [ ] **MAC-030** (P0) Open With on a cold start
  1. Quit the app.
  2. Run `open -a …/Campfire.app ~/fixtures/<song>.cho` against the **release** bundle.

  **Expected:** the app starts and imports that one song, exactly once.
  <sub>[r2-44]</sub>
- [ ] **MAC-031** (P1) Open With while the app is running
  1. With the app running (also try with it minimized), open two songs from Finder with Open With → Campfire.

  **Expected:** the window comes forward and one import brings both songs.
  <sub>[r2-44]</sub>
- [ ] **MAC-032** (P1) Open With leaves the editor alone
  1. Leave unsaved text in the editor.
  2. Open a song from Finder.

  **Expected:** the import runs and the editor and its text are untouched. The imported song does **not** open over
  the editor.
  <sub>[r2-44][presentation]</sub>
- [ ] **MAC-033** (P1) File associations in Finder
  1. Copy the release app to /Applications.
  2. In Finder, look at Open With for a `.png`, for `song.cho`, for a `.chopro`, and for an `x.pro` claimed by
     another app. Double-click `song.cho`.

  **Expected:** Campfire is not listed for the `.png`. For `song.cho` it is offered, and is the default where nothing
  else claims the type. `.chopro` is offered. For `x.pro` it is listed but is not the default. Double-clicking
  `song.cho` imports it.
  <sub>[r4-16]</sub>
- [ ] **MAC-034** (P2) The bundle's document types
  1. Run `plutil -p …/Campfire.app/Contents/Info.plist`.

  **Expected:** one `CFBundleDocumentTypes` with two entries (`org.chordpro.cho` as Default, `org.chordpro.chordpro`
  as Alternate), both types in `UTImportedTypeDeclarations`, and no `CFBundleTypeOSTypes`. `plutil -lint` reports OK.
  <sub>[r4-16]</sub>
- [ ] **MAC-035** (P0) A second launch hands over to the first
  1. Start the packaged binary.
  2. Start it a second time with a song as its argument.

  **Expected:** there is no second window. The first comes forward and imports the song, and the second process exits
  within about a second. Both `instance.lock` and `instance.endpoint` exist, and the endpoint's permissions are
  `-rw-------`.
  <sub>[r2-49]</sub>
- [ ] **MAC-036** (P1) Recovery after a crash
  1. Kill the app with `kill -9`, then start it again.

  **Expected:** it starts normally, and `instance.endpoint` has a new port and token.
  <sub>[r2-49]</sub>
- [ ] **MAC-037** (P1) Relaunch while closing
  1. In a loop, quit the app and immediately open a `.cho` with it (a small `osascript` loop does this).

  **Expected:** every time, the song opens in the new window and none is lost.
  <sub>[r3-24]</sub>
- [ ] **MAC-038** (P2) A bad token is refused
  1. Send `printf 'CAMPFIRE 1 wrong\n/etc/hosts\n' | nc 127.0.0.1 <port from instance.endpoint>`.

  **Expected:** no `OK` comes back and nothing is imported.
  <sub>[r2-49]</sub>
- [ ] **MAC-039** (P1) Drag and drop of files and a folder
  1. Drop a few songs onto the window.
  2. Drop a folder holding songs, a `.DS_Store`, a `._song.cho` and a subfolder.

  **Expected:** the dropped songs are imported. From the folder, only the songs directly inside it are imported, and
  the rest is silently ignored. The window keeps repainting while a large drop is read.
  <sub>[r2-43][r2-15]</sub>
- [ ] **MAC-040** (P2) Dropping while an import runs
  1. Drop files while another import is running.

  **Expected:** they are queued and imported after it, not dropped.
  <sub>[r1-53]</sub>

## 4. The library folder from Finder

- [ ] **MAC-050** (P0) A decomposed (NFD) name from Finder stays one song
  1. Copy fixture: NFD title (a song whose file name is in decomposed form) into `library/songs`. Its NFC twin should
     already be in the library.
  2. Switch back to Campfire.
  3. Use Update file name on it, and run a sync if one is connected.

  **Expected:** there is one song, not two, and there is no duplicate after Update file name. With sync connected,
  Dropbox also holds only one file for it.
  <sub>[r5-07]</sub>
- [ ] **MAC-051** (P1) Changes from Finder are picked up
  1. Settings → Library → Location opens the folder in Finder.
  2. Add, edit and delete a `.cho` there, then switch back to the app.

  **Expected:** the list follows each change when the window regains focus. No screen has pull to refresh.
  <sub>[presentation][app/desktop]</sub>
- [ ] **MAC-052** (P0) An export names the files it could not read
  1. Make one song unreadable (`chmod a-r library/songs/x.cho`).
  2. Export the whole library.

  **Expected:** the export finishes with a message naming `x.cho` as left out. The zip holds every other file.
  Restore the file with `chmod u+r`.
  <sub>[r5-06]</sub>
- [ ] **MAC-053** (P0) An export whose scan failed does not produce a partial backup
  1. Make `library/songs` unlistable (`chmod a-rx`).
  2. Export the whole library.

  **Expected:** the export **fails** with a message. There is no half-empty zip passed off as a backup. Restore the
  folder afterwards.
  <sub>[r5-06]</sub>
- [ ] **MAC-054** (P1) Unreadable files do not crash the app
  1. Make a song unreadable (`chmod a-r`), then open it and its editor.
  2. Make `preferences/preferences.json` unreadable.

  **Expected:** the song and editor show their error state. The preferences read shows an error with Retry. Nothing
  crashes.
  <sub>[r2-46][r2-30]</sub>
- [ ] **MAC-055** (P1) Finder's helper files are ignored
  1. Put `._song.cho` and `.DS_Store` into `library/songs`.

  **Expected:** the song list does not show them.
  <sub>[r2-27]</sub>
- [ ] **MAC-056** (P1) A huge file is skipped
  1. Run `mkfile 600m ~/Library/Application\ Support/Campfire/library/songs/huge.cho`.
  2. Start the app. With sync connected, press Sync now.

  **Expected:** the app opens with every other song. With sync, `huge.cho` is named among the failed files and there
  is no 600 MB upload. After deleting the file, the next run is clean.
  <sub>[r3-06]</sub>
- [ ] **MAC-057** (P1) A 2,000-song library
  1. Generate a 2,000-song library with fixture: large library generator and start the app.

  **Expected:** the launch hands over smoothly and the list fills without freezing. Scrolling and search stay fluid.
  <sub>[r1-31]</sub>
- [ ] **MAC-058** (P1) Right-to-left lyrics
  1. Import fixture: RTL song (Hebrew, four chords per line).
  2. Open it, then make the window narrow until the lines wrap.

  **Expected:** the lines are right-aligned and each chord sits over its word (`Am` at the right, `F` at the left),
  with none stacked. When a line wraps, each visual line starts from the right again. The demo songs still read left
  to right.
  <sub>[r5-35]</sub>

## 5. The packaged runtime and the release bundle

- [ ] **MAC-060** (P1) Language names are localized in the packaged app
  1. In the packaged app, set the language to Hungarian.
  2. Open the Songs filter with at least two languages in the library.

  **Expected:** the chips read *angol* / *magyar*, not English / Hungarian. The language chip on song details agrees.
  <sub>[r5-17]</sub>
- [ ] **MAC-061** (P2) The runtime carries the two added modules
  1. Run `grep -o 'MODULES="[^"]*"' …/Campfire.app/Contents/runtime/Contents/Home/release`.

  **Expected:** the list contains `jdk.accessibility` and `jdk.localedata`.
  <sub>[r5-17]</sub>
- [ ] **MAC-062** (P1) VoiceOver in the packaged app
  1. Turn VoiceOver on (Cmd+F5) and move through the Songs screen, a song, and Settings.

  **Expected:** rows, app bar actions and the tag remove button are announced by name. A chorded line reads with its
  chords in brackets.
  <sub>[r5-17][r2-63]</sub>
- [ ] **MAC-063** (P1) The About section on a direct download
  1. Open Settings → About on the default (`download`) build.

  **Expected:** one untitled section with these rows: the author and version, GitHub, Report a problem, "Every version
  of Campfire", Privacy policy, and Buy me a coffee. There is **no** rating row, because the Mac App Store listing URL
  is still empty. There are no per-platform "Coming soon" rows. Every link opens in the default browser.
  <sub>[r5-16][r5-15]</sub>
- [ ] **MAC-064** (P1) The Mac App Store build hides the donation link
  1. Build with `-Pcampfire.desktop.distribution=mac-app-store`.
  2. Open Settings → About.

  **Expected:** the coffee row is gone and everything else is the same.
  <sub>[r5-15]</sub>
- [ ] **MAC-065** (P2) A mistyped distribution value fails the build
  1. Build with `-Pcampfire.desktop.distribution=appstore`.

  **Expected:** Gradle fails at configuration, naming the four allowed values.
  <sub>[r5-15]</sub>
- [ ] **MAC-066** (P0) The downloaded `.dmg` installs and runs
  1. Download the `.dmg` from the GitHub release on Apple silicon and install it.
  2. On the first start, override Gatekeeper (System Settings → Privacy & Security → Open Anyway).

  **Expected:** the app starts, is ad-hoc signed, and plants the demo library in an empty data folder.
  <sub>[CLAUDE]</sub>
- [ ] **MAC-067** (P2) No JVM warning at startup
  1. Start the packaged binary from Terminal.

  **Expected:** no `--add-opens` or illegal-access warning is printed.
  <sub>[r5-23]</sub>

## 6. Links and sync from the desktop

- [ ] **MAC-070** (P0) Connect Dropbox from the desktop
  1. Settings → Library → Connect.

  **Expected:** the consent page opens in the **default** browser, the same way the About links do. After allowing,
  the browser shows the done page and the app shows the account. The credentials are in
  `preferences/sync-credentials.json`.
  <sub>[r5-31][app/desktop]</sub>
- [ ] **MAC-071** (P1) Connect is not blocked by port 53682
  1. Run `nc -l 53682` in Terminal.
  2. Press Connect.

  **Expected:** the connection fails with a message saying so, and does not hang. Stop `nc`, and Connect works.
  <sub>[remote-impl]</sub>
- [ ] **MAC-072** (P2) A link that cannot be opened
  1. **(dev patch)** Force the link opener to fail.

  **Expected:** a snackbar names the address instead of nothing happening.
  <sub>[r2-45][r5-31]</sub>

## The Mac App Store build

Run this section only once a sandboxed Mac App Store build exists (a `.pkg` target, entitlements and the sandbox).
Until then there is nothing to run it against. Test these:

- The library lives in `~/Library/Containers/com.pandulapeter.campfire/Data/Library/Application Support/Campfire`,
  and the Location row points there.
- Open With and drag and drop work under the sandbox.
- The system file panels are used for import and export.
- Dropbox Connect works end to end through `127.0.0.1:53682` with the `network.server` entitlement.
- The single-instance listener works.
- The donation link is absent.

<sub>[pub-mas][r5-15][r5-26]</sub>
