<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Manual tests: Windows desktop

Windows differs from the Mac in ways that only a real PC shows:

- it refuses to replace or delete a file that another process has open (an antivirus, the indexer, Explorer's
  preview), and the app retries through that;
- some names are illegal (`? : * " < > |`, a trailing dot or space, `CON`, `NUL`…);
- per-user MSI installs and upgrades;
- `toFront()` only flashes the taskbar;
- AltGr keyboard layouts;
- Narrator, through the Java Access Bridge.

Functional behaviour shared with every platform is in `00-core-functional.md`. If you have the time, run its P0
tests on Windows too.

## Before you start

- **Artifact.** Use the MSI from the latest GitHub release, `campfire-<version>-windows-x64-unsigned.msi` (the output of
  `desktop-publish.yml`). This is exactly what users get: ProGuard'd, with a jlink runtime, installed per user.
  - Or build it on the PC:
    `git clone …`, then `gradlew.bat :app:desktop:packageReleaseMsi`. Gradle provisions the JDK 21 toolchain itself
    (foojay), and the Compose plugin downloads WiX, so neither needs installing. The output is in
    `app\desktop\build\compose\binaries\main-release\msi\`.
  - To test sync, create `local.properties` with `campfire.dropbox.appKey=…` before building. A release MSI already
    carries the key.
  - For the quick loop, `gradlew.bat :app:desktop:run` works too. Only the MSI proves the packaged runtime.
- **Library folder:** `%APPDATA%\Campfire` (`library\songs`, `library\setlists`, `preferences\`, `instance.lock`,
  `instance.endpoint`). Settings → Library → Location shows it.
- **Keep Windows Defender real-time protection ON.** If a second antivirus is available, test with it too. Scanners
  are what the locking fix is for.
- **Fixtures:** copy the `campfire-fixtures\` folder made by `documentation/testing/fixtures/make_fixtures.py` onto
  the PC. For WIN-040 to WIN-043 you also need a Dropbox account and a second device (the Mac).
- **Keyboard layouts:** add Polish (Programmer), German and Russian in Settings → Time & language.
- **Time:** about 2.5 hours.
- **Record:** the Windows version and build (`winver`), the display scaling %, the antivirus, and the MSI version.
  For failures, a screenshot plus any `hs_err_pid*.log` next to the app. The Windows launcher writes no console log.

Legend: **P0** can lose or corrupt the library, or blocks the release. **P1** is visible breakage. **P2** is polish.

## Smoke (10 minutes)

- [ ] **WIN-S01** (P0) The MSI installs without an administrator
  1. Double-click the MSI on a standard (non-admin) account.

  **Expected:** there is no UAC prompt. The install finishes, and a Start menu entry and a desktop shortcut exist.
  <sub>[pub-ms][app/desktop]</sub>
- [ ] **WIN-S02** (P0) The first start
  1. Start Campfire from the Start menu.

  **Expected:** the launch mark fades into the Songs screen with the demo songs and setlist.
  `%APPDATA%\Campfire\library\songs` holds the two `.cho` files. The process stays up.
  <sub>[CLAUDE]</sub>
- [ ] **WIN-S03** (P0) Edit, save, restart
  1. Edit a song, press Ctrl+S and close the window.
  2. Start the app again.

  **Expected:** the edit is kept.
  <sub>[features]</sub>
- [ ] **WIN-S04** (P1) Import and export through the file dialogs
  1. Import a song through the file dialog.
  2. Export a song, then export the whole library.

  **Expected:** the native Windows dialogs open, and the files land where they were chosen. The zip opens in Explorer
  and its dates are today.
  <sub>[r2-31]</sub>

## 1. File locking and antivirus

- [ ] **WIN-001** (P0) Import while the scanner is busy
  1. With Defender on, import fixture: zip archive (a few hundred songs).
  2. While it runs, click out of the window and back in a few times, which triggers rescans.

  **Expected:** every song is imported, no file is reported as failed, and no "could not be written" message appears.
  <sub>[r5-02]</sub>
- [ ] **WIN-002** (P0) Saving a file Explorer has open
  1. Open a song in Explorer's preview pane (select it with Alt+P on). In Campfire, edit the same song and save, ten
     times in a row.
  2. Keep the song open in Notepad, save again from Campfire, then delete the song from Campfire.

  **Expected:** every save succeeds, and the file on disk holds the last text. The delete succeeds, or fails with a
  clear message. It never fails silently.
  <sub>[r5-02]</sub>
- [ ] **WIN-003** (P0) A first sync of a few hundred songs
  1. Connect the Dropbox that holds fixture: large library (a few hundred songs).
  2. Press Sync now and scroll the Songs list while it runs.

  **Expected:** the run finishes clean, with no "files could not be synced". "Last synced" shows the current time.
  <sub>[r5-02]</sub>
- [ ] **WIN-004** (P1) Renaming and deleting are retried, not failed
  1. Use Update file name on a song, then delete a setlist, with Defender scanning.

  **Expected:** both succeed on the first try from the user's point of view.
  <sub>[r5-02]</sub>
- [ ] **WIN-005** (P1) Reading never locks the file
  1. With Campfire running, edit a library `.cho` in Notepad and save.
  2. Switch back to Campfire.

  **Expected:** Notepad saves without a "file is in use" error, and Campfire shows the new text.
  <sub>[r5-02]</sub>

## 2. File names

- [ ] **WIN-010** (P0) A song titled "Con"
  1. Create a song titled `Con` with no artist.

  **Expected:** the file is saved (as `_con.cho`), the list shows "Con", and there is no Update file name entry.
  Restart the app: the song is still there.
  <sub>[r2-29]</sub>
- [ ] **WIN-011** (P1) Device names inside an archive
  1. Import a zip holding `con.cho`, `nul.setlist.json` and `aux.cho` (fixture: Windows-illegal-names folder).

  **Expected:** they are imported under escaped names, work normally, and export back out under their own names.
  <sub>[r2-29][r5-03]</sub>
- [ ] **WIN-012** (P1) Non-Latin and long names
  1. Import `Катюша.cho`, `Gęsi za wodą.cho` and a Hebrew song, plus a zip made with Windows' own "Send to →
     Compressed folder" holding `Tükörfúrógép.cho`.
  2. Import a 240-character name.

  **Expected:** the names are kept in their own letters and the accents are right. The long name is shortened, not
  refused.
  <sub>[r2-26][r4-13][r2-29]</sub>
- [ ] **WIN-013** (P1) Old Windows encodings
  1. Import the fixture cp1250 Hungarian file and the fixture cp1252 French file.

  **Expected:** `Árvíztűrő tükörfúrógép` and `Café à la crème` read correctly.
  <sub>[r1-05][r4-06]</sub>
- [ ] **WIN-014** (P2) A pasted two-line tag
  1. Paste two lines (CRLF) into Add tag.

  **Expected:** one chip is created, and the rest of the file is unchanged.
  <sub>[r3-39]</sub>

## 3. Sync with names Windows cannot store

- [ ] **WIN-040** (P0) A cloud file Windows cannot hold is skipped and named once
  1. On the Mac, put fixture: Windows-illegal-names folder's `Who Are You?.cho` and `AC|DC - Thunder.cho` into the
     Dropbox folder `Apps/Campfire Sync/songs`, through the Dropbox website or the Mac library.
  2. Sync on Windows.

  **Expected:** the run finishes, every other file is in step, and the message names each of those files once as not
  syncable on this device, promising no retry. "Last synced" still advances for the rest.
  <sub>[r5-03]</sub>
- [ ] **WIN-041** (P1) A skipped file is not reported again
  1. Sync on Windows twice more.

  **Expected:** there are no repeated failures and nothing is uploaded or deleted for those names.
  <sub>[r5-03]</sub>
- [ ] **WIN-042** (P1) A renamed file arrives
  1. On the Mac, rename `Who Are You?.cho` to a legal name and sync.
  2. Sync on Windows.

  **Expected:** Windows now downloads it.
  <sub>[r5-03]</sub>
- [ ] **WIN-043** (P0) The deletion guard, seen from Windows
  1. Sync Windows with a library of at least 10 songs.
  2. Rename the Dropbox folder `Apps/Campfire Sync` in the Dropbox website.
  3. Sync on Windows.

  **Expected:** the run stops before anything moves and asks. Choose **Keep them and upload**: everything goes back
  up and nothing is lost locally.
  <sub>[r5-01][sync.md]</sub>

## 4. Install, upgrade and uninstall

- [ ] **WIN-020** (P0) Upgrading keeps the library
  1. Install an older release's MSI and make some songs.
  2. Install the new MSI over it.

  **Expected:** the new version replaces the old one in place, with one entry in Apps & features. The library in
  `%APPDATA%\Campfire` is untouched.
  <sub>[app/desktop][pub-ms]</sub>
- [ ] **WIN-021** (P1) Uninstalling keeps the library
  1. Uninstall from Apps & features.

  **Expected:** the program files are gone and `%APPDATA%\Campfire` is left in place. A reinstall finds the library.
  <sub>[app/desktop]</sub>
- [ ] **WIN-022** (P2) Unsigned installer warning
  1. Download the MSI through Edge or Chrome and run it.

  **Expected:** a SmartScreen "unrecognized app" warning appears, which "More info → Run anyway" gets past. This is
  expected for an unsigned build, so note what the warning says.
  <sub>[CLAUDE]</sub>

## 5. File associations and the single instance

- [ ] **WIN-030** (P1) Only three extensions are claimed
  1. Double-click `song.cho`, `song.chopro` and `song.chordpro`.
  2. Double-click a `.pro` and a `.txt`.

  **Expected:** the first three open in Campfire. The `.pro` and the `.txt` keep their previous handler.
  <sub>[r4-16]</sub>
- [ ] **WIN-031** (P0) Opening several files with the app closed
  1. With Campfire closed, select five `.cho` files in Explorer and press Enter.

  **Expected:** there is exactly one window, and all five are imported in one import.
  <sub>[r2-49]</sub>
- [ ] **WIN-032** (P1) The running window comes to the front
  1. Put Campfire behind another window, or minimize it.
  2. Double-click a `.cho` in Explorer.

  **Expected:** the Campfire window comes to the **front**, not just a flashing taskbar button, and the song is
  imported.
  <sub>[app/desktop][r2-49]</sub>
- [ ] **WIN-033** (P1) Relaunch while closing
  1. Close Campfire and immediately double-click a `.cho`. Repeat five times.

  **Expected:** each time, the song opens in the new window.
  <sub>[r3-24]</sub>
- [ ] **WIN-034** (P1) Drag and drop from Explorer
  1. Drop files, and a folder, from Explorer onto the window.

  **Expected:** they are imported. Only the files directly inside the folder are imported.
  <sub>[r2-43]</sub>

## 6. Keyboard and window

- [ ] **WIN-050** (P1) AltGr on a Polish keyboard
  1. With the Polish (Programmer) layout, type `ąśżźł` in the editor, then press AltGr+S.

  **Expected:** the letters are typed, and AltGr+S types `ś` and does **not** save.
  <sub>[r3-20]</sub>
- [ ] **WIN-051** (P1) AltGr on a German keyboard
  1. With the German layout, type `@ { } [ ]` with AltGr.

  **Expected:** the characters are typed and nothing fires as a shortcut.
  <sub>[r3-20]</sub>
- [ ] **WIN-052** (P1) Ctrl+S on a Russian keyboard
  1. With the Russian layout, press Ctrl+S in the editor.

  **Expected:** the song is saved.
  <sub>[r3-20]</sub>
- [ ] **WIN-053** (P1) Modified arrow keys on song details
  1. On song details in a setlist, press Alt+Left and Alt+Right, then Ctrl+Left and Ctrl+Right.

  **Expected:** none of them pages or scrolls the song. Plain arrows still scroll and page.
  <sub>[r5-34]</sub>
- [ ] **WIN-054** (P1) Ctrl+F and Escape
  1. Press Ctrl+F on both list screens.
  2. Press Escape to close layers, as in MAC-021.

  **Expected:** the search opens with Ctrl+F. Escape closes one layer at a time and quits on the root screen.
  <sub>[presentation][r3-19]</sub>
- [ ] **WIN-055** (P1) Closing the window with unsaved text
  1. Close the window with unsaved editor text and try each of the three answers.

  **Expected:** Save writes the file and closes; Discard closes; Cancel stays.
  <sub>[r5-26]</sub>
- [ ] **WIN-056** (P1) Display scaling
  1. Set display scaling to 100%, 150%, then 200%, and move the window between two monitors with different scaling.

  **Expected:** the text is sharp and the layout is correct, with no blurry bitmap scaling. The window's minimum is
  400×400.
  <sub>[app/desktop]</sub>
- [ ] **WIN-057** (P2) The ANSI code page
  1. **(optional)** Set the system locale to Hungarian (Region → Administrative), then reboot.

  **Expected:** names with `ő ű` are still stored and shown correctly.
  <sub>[r2-26]</sub>

## 7. The packaged runtime, links and accessibility

- [ ] **WIN-060** (P1) Language names are localized
  1. Set the app to Hungarian and open the language filter.

  **Expected:** the chips read *angol* / *magyar*.
  <sub>[r5-17]</sub>
- [ ] **WIN-061** (P1) Narrator reads the app
  1. Run `jabswitch /enable` from `%LOCALAPPDATA%\Programs\Campfire\runtime\bin` (or the install folder's
     `runtime\bin`), then sign out and back in.
  2. Start Narrator (Win+Ctrl+Enter) and Tab through Songs, a song and Settings.

  **Expected:** rows, app bar actions and buttons are announced by name.
  <sub>[r5-17]</sub>
- [ ] **WIN-062** (P0) Connect Dropbox
  1. Settings → Library → Connect.

  **Expected:** the consent page opens in the **default** browser. A firewall prompt for `127.0.0.1:53682` may
  appear; allow it for private networks. After consent, the app shows the account.
  <sub>[r5-31][pub-ms]</sub>
- [ ] **WIN-063** (P1) The About section on Windows
  1. Open Settings → About.

  **Expected:** there is no rating row, because the Microsoft Store listing URL is still empty. The coffee row is
  shown. Every link opens in the default browser.
  <sub>[r5-16][r5-15]</sub>
- [ ] **WIN-064** (P1) Right-to-left lyrics
  1. Import fixture: RTL song.

  **Expected:** the lines are right-aligned and the chords spread over their words, as in MAC-058. This checks the
  text shaping of the Windows fonts.
  <sub>[r5-35]</sub>
- [ ] **WIN-065** (P2) The Location row
  1. Open Settings → Library → Location.

  **Expected:** it shows the `%APPDATA%\Campfire` path and opens it in Explorer.
  <sub>[app/desktop]</sub>

## The Microsoft Store build

Run this section only once a Microsoft Store (MSIX) build exists. Until then there is nothing to run it against.
Test these:

- The packaged app's `%APPDATA%` redirection (`%LOCALAPPDATA%\Packages\…\LocalCache\Roaming`), and what the Location
  row shows.
- Install a self-signed sideload before submitting.
- The rating row appears once the listing URL is filled in.

<sub>[pub-ms]</sub>
