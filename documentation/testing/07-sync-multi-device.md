<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Dropbox sync across devices — manual test script

Written 2026-09-23 against `master` at `2740892c`. Sync is the one part of Campfire that can destroy a library that is
not on the device being used: a wrong deletion propagates to every device on the next run. **None of the checks below
has ever been run against a real account.** This is the most important document of the set; section 3 (deletions and
the deletion guard) is the most important part of it.

The rules being tested, in one breath (details in `documentation/sync.md`): content decides what changed, never a
clock; an edit beats a deletion; a file changed on both sides is never merged — the local one keeps the name and the
other lands next to it as `<name> (2).cho`; a rename is a deletion plus a new file; a run that would delete most of the
library **on this device** stops and asks, and so does one that would delete most of the **cloud folder** because the
files are gone from this device (and it always asks when this device's library is empty); an interrupted run is
reported next time and not restarted by itself.

`🆕` marks tests of something changed in this round. **P0** = can lose or corrupt a library, **P1** = visible
breakage, **P2** = polish. Single-device sync behaviour that is specific to one platform (the web's consent page, the
Android foreground notification, the iOS background task) is in that platform's own document.

## Before you start

- **The account.** Use the throwaway Dropbox account only; it will be wiped. Never connect a real one while running
  this document. Everything Campfire touches is `Apps/Campfire/songs` and `Apps/Campfire/setlists` in that account
  (app-folder permission). "dropbox.com" below means that folder in the Dropbox web UI, standing in for a device.
- **Builds.** Every build must have `campfire.dropbox.appKey` in `local.properties`; a build without it says "This
  build of Campfire was made without sync credentials…" in Settings → Library. Registered redirects: `campfire://oauth`
  (Android, iOS), `http://127.0.0.1:53682` (desktop), `http://localhost:8080/` and
  `https://pandulapeter.com/campfire/` (web).
- **Device matrix.** The minimum is two devices; the full pass uses five:

  | Name | Device | Library location |
  | --- | --- | --- |
  | **MAC** | macOS desktop build | `~/Library/Application Support/Campfire/library` |
  | **WIN** | Windows desktop build (MSI or `:app:desktop:run`) | `%APPDATA%\Campfire\library` |
  | **AND** | Android phone (or emulator), debug or release | app-private; `adb shell run-as com.pandulapeter.campfire.debug ls -R files` |
  | **IOS** | iPhone (or simulator) | Files → On My iPhone → Campfire |
  | **WEB** | Chrome, dev server `localhost:8080` or the deployed site | OPFS (see `06-web.md`) |

  Where a test says "A" and "B", any two of these will do; where it names one, that platform matters.
- **What to look at.** Settings → Library → the sync section: "Connected as …", "Last synced successfully on …" (the
  exact local date and time), the progress row "Syncing N of M…", and the summary lines ("N files could not be
  synced. Among them: …", "Kept both versions of …"). On desktop, `preferences/sync-index.json` next to the library
  is what the last successful run saw; `"isRunInProgress": true` in it while a run is going.
- **Slow network**, where a test needs a run to still be going: macOS Network Link Conditioner ("Very Bad Network" or
  "100% Loss"), Chrome DevTools throttling, the Android emulator's network speed setting.
- **Fixtures** (see `README.md`): fixture: the Windows-illegal-names folder, fixture: NFD vs NFC titles, fixture: the
  large library generator, fixture: the keep-both pair.
- **Resetting between sections:** Disconnect on every device (leaves files on both sides), empty `Apps/Campfire` on
  dropbox.com, and clear or replace each library. A disconnect forgets the account and the index; reconnecting the
  same account compares by content.

---

## 1. Connecting, the account, disconnecting

- [ ] **SYNC-001** (P0) Every platform connects and names the account
  1. On MAC, WIN, AND, IOS and WEB in turn: Settings → Library → Connect Dropbox, log in, Allow.
  **Expected:** each returns to the app on Settings → Library showing "Connected as <display name>" and runs a first
  sync. Only `Apps/Campfire/songs` and `…/setlists` appear in the account. On desktop, `sync-index.json` is keyed
  `dropbox:dbid:…`, not by e-mail. (Desktop: the consent page opens in the default browser and the tab then says
  "Campfire is connected — You can close this tab and go back to the app.")
  <sub>[r2-20][sync.md][remote-impl]</sub>

- [ ] **SYNC-002** (P1) Cancel and failure on the way in
  1. Connect, then press Cancel on the "Waiting for the browser…" row before approving.
  2. Connect, press "Cancel" on Dropbox's own consent page.
  3. Desktop: Connect, close the browser tab, Cancel; Connect again at once. Cancel + Connect quickly.
  4. Double-click Connect.
  **Expected:** 1: the plain Connect button, not "Could not reach the service". 2: a reason under Connect ("Dropbox
  refused the connection." or "The connection did not go through."), not a silent return. 3: the button returns at
  once and a second Connect opens exactly one new browser tab (the loopback port was released). 4: one consent page.
  <sub>[r2-07][r2-21][r3-13][r1-22]</sub>

- [ ] **SYNC-003** (P1) Offline and revoked accounts
  1. Connect and sync on MAC. Quit, turn the network off ("100% Loss"), start Campfire.
  2. Network back. On dropbox.com → Settings → Connected apps, remove Campfire. Press Sync now on MAC without
     restarting.
  3. Connect the same account again, Sync now.
  **Expected:** 1: the account name shows at once (never the Connect button), the launch sync ends "Could not reach
  the service. Campfire will try again next time." 2: the section becomes Connect Dropbox with "Dropbox refused the
  connection." 3: connects; a song deleted before the revoke stays deleted.
  <sub>[r2-19][r4-01][r1-23]</sub>

- [ ] **SYNC-004** (P1) Clock skew does not look like an expired login
  1. After a sync, set MAC's clock back 5 hours; Sync now with ~50 changed files. Set it right again.
  **Expected:** the run completes; it never asks to reconnect.
  <sub>[r3-45][r1-18]</sub>

- [ ] **SYNC-005** (P1) Disconnect leaves the files alone
  1. Disconnect on MAC (confirm the "Disconnect <name>?" question). Look at the library and at dropbox.com.
  2. Desktop: press Stop on a running sync and immediately Disconnect.
  **Expected:** nothing is removed on either side; `sync-credentials.json` and `sync-index.json` are gone from MAC's
  `preferences`. 2: `sync-index.json` does not exist afterwards.
  <sub>[r1-17][r2-24][sync.md]</sub>

- [ ] **SYNC-006** 🆕 (P0) A fresh installation never inherits a connection
  1. IOS: connect and sync. Delete the app. Reinstall it (from Xcode / TestFlight) and open it.
  2. AND: connect and sync. Uninstall, reinstall, open. (And an Android backup restore, if a spare phone is at hand:
     see `01-android.md`.)
  3. MAC: connect and sync, quit, start again.
  **Expected:** 1 and 2: Settings shows Connect Dropbox — the Keychain entry that survived the uninstall is not
  used, no run starts on its own. Connecting again compares by content: no `(2)` copies, nothing re-uploaded that is
  already the same, the demo songs are **not** uploaded as new files (Dropbox gains nothing). 3: still connected
  (a quit is not a fresh installation).
  <sub>[r5-25][r2-25][CLAUDE]</sub>

## 2. Ordinary runs

- [ ] **SYNC-010** (P0) A song travels both ways between every pair
  1. Create a song on MAC, Sync now. Sync now on WIN, AND, IOS, WEB.
  2. Edit it on AND, sync; sync the rest. Edit it on WEB, sync; sync the rest.
  3. Create a setlist on IOS containing it, reorder, archive it, give it a description; sync all.
  **Expected:** every device shows the same text and the same setlist (order, archived state, description, per-entry
  transposition). A second Sync now on any device plans nothing ("Last synced" moves, nothing transfers).
  <sub>[sync.md]</sub>

- [ ] **SYNC-011** (P1) "Last synced" is exact and only moves on success
  **Expected:** after each clean run the line reads "Last synced successfully on <local date> at <hh:mm>" in the
  device's time zone, in the order the app's language puts it (switch to Hungarian once to check).
  <sub>[sync.md][r2-16]</sub>

- [ ] **SYNC-012** (P1) Only songs and setlists are synced
  1. On dropbox.com put `notes.txt`, a `.DS_Store`, a `._song.cho` and a real `.cho` into `songs/`, and fixture: the
     8 MiB+ song.
  2. Sync now twice on MAC; restart; sync.
  **Expected:** the real song arrives; `notes.txt` and the hidden files stay in Dropbox and never appear in
  `library/songs`; the oversized song is named as not synced ("One file could not be synced: …") and everything else
  moves. Hidden local files are never uploaded.
  <sub>[r2-01][r2-27][sync.md]</sub>

- [ ] **SYNC-013** 🆕 (P0) A name written two ways is one song (Unicode NFC)
  1. On IOS (or MAC) create a song titled with a Cyrillic or accented title from fixture: NFD vs NFC titles (e.g.
     `Катюша` by `Кино`, and one with a decomposed `й`). Sync.
  2. On dropbox.com (or via the API, see section 7), upload the **decomposed** spelling of the same name with
     different content.
  3. Sync MAC, AND, WEB, WIN twice each.
  **Expected:** one file per song on Dropbox and on every device — never two that look identical. The decomposed
  upload is treated as the same name (a conflict copy ` (2)` if the contents differ, otherwise nothing). A second
  run on each device plans nothing.
  <sub>[r5-07][r2-26]</sub>

- [ ] **SYNC-014** 🆕 (P1) A name Windows cannot store is skipped on Windows and named, not failed forever
  1. Upload fixture: the Windows-illegal-names folder into `Apps/Campfire/songs` (names with `? : * " < > |`, and a
     trailing space or dot if Dropbox accepts it).
  2. Sync WIN. Sync MAC.
  3. On MAC, rename one of them to a legal name with "Update file name" (or on dropbox.com), sync MAC, then WIN.
  **Expected:** 2: WIN syncs every other file and names the illegal ones in "… could not be synced. Among them: …";
  its "Last synced successfully" is not moved while they are there; they stay untouched in Dropbox. MAC receives them
  normally. 3: the renamed one arrives on WIN. Nothing crashes, nothing loops.
  <sub>[r5-03][sync.md]</sub>

- [ ] **SYNC-015** (P1) The screens follow a run
  1. On A open a song, a setlist and Settings. On B edit that song and the setlist, sync B. Sync A.
  **Expected:** A's details screen shows the downloaded text in place; the setlist and the library size row update
  without leaving the screen; an editor open on that song asks about unsaved changes on leaving.
  <sub>[r1-02][r4-45]</sub>

- [ ] **SYNC-016** (P1) A setlist edited during a run keeps both changes
  1. On B add a third song to setlist S, sync B.
  2. On A (large library so the run lasts), Sync now; while it runs open Edit on S, type a description, Save after
     the run finishes.
  3. Repeat with a transposition tap on the setlist while the progress row shows.
  **Expected:** S has the third song **and** the description / transposition, on A and after syncing, on B. If S is
  deleted remotely behind the open dialog, Save shows the failure and S stays deleted.
  <sub>[r2-33][r3-02]</sub>

- [ ] **SYNC-017** (P1) Rename reaches the other device as delete + new; an edit of the old name survives
  1. On A, "Update file name" on a song; sync A; sync B.
  2. Again with a different song: rename on A, but on B edit that song (old name) before syncing B; sync A, then B,
     then A.
  3. Case-only rename: `Hallelujah.cho` with `{title: Hallelujah}`, sync, Update file name (→ `hallelujah.cho`), sync,
     delete it, sync.
  **Expected:** 1: B ends with the new name only; setlists on B point at the new name. 2: both files remain
  everywhere (the edit beat the deletion). 3: it stays deleted; Dropbox no longer lists `Hallelujah.cho`; no endless
  conflict.
  <sub>[CLAUDE][sync.md][r4-04][r1-30]</sub>

- [ ] **SYNC-018** (P1) A large first sync is rate limited, not failed
  1. Generate fixture: the large library (2,000+ songs) on MAC. Connect to an empty folder; Sync now.
  2. Then connect an empty AND or WEB to that folder.
  **Expected:** both runs complete; "Syncing N of M…" moves steadily; the song list grows during the run; the final
  count is exact; no file is named as failed; the UI stays responsive.
  <sub>[sync.md][r1-16][r1-29][r2-23]</sub>

- [ ] **SYNC-019** (P1) Importing and a rescan during a run do not fail files
  1. Import a few hundred songs on MAC, press Sync now, and switch away from and back to the app during the run
     (a rescan on resume).
  **Expected:** no file reported failed.
  <sub>[r5-02]</sub>

## 3. Deletions and the deletion guard (the section that can destroy a library)

Prepare: at least 12 songs synced to two devices A and B (MAC and WEB are the quickest pair), plus one setlist.

- [ ] **SYNC-030** (P0) An edit beats a deletion
  1. Delete song X on A and sync A. Before syncing B, edit X on B. Sync B, then A.
  **Expected:** X survives on A, B and Dropbox with B's edit.
  <sub>[sync.md]</sub>

- [ ] **SYNC-031** (P0) A few deletions propagate normally
  1. Delete 3 of 12 songs on A, sync A, sync B.
  **Expected:** no question; the 3 are gone on B and on Dropbox.
  <sub>[sync.md]</sub>

- [ ] **SYNC-032** (P0) An emptied cloud folder stops the run and asks (this device's side)
  1. On dropbox.com delete the whole `songs` folder.
  2. Sync now on A.
  3. Answer **Keep them and upload**.
  4. Delete the folder again, sync A, answer **Delete them here too**.
  **Expected:** 2: the run stops **before anything moves** and asks "12 of your 12 synced files are gone from the
  cloud folder. Delete them here too, or keep them and upload them again?"; the library is intact; a dot appears on
  the Library tab while the question waits; every ordinary run (a relaunch included) asks again until answered.
  3: the folder is repopulated, B unaffected. 4: the songs are deleted on A, then on B at its next run.
  <sub>[r1-11][sync.md][presentation]</sub>

- [ ] **SYNC-033** (P0) The threshold
  1. With 12 synced songs, delete 6 on dropbox.com → sync A. Then 7 → sync A. With 5 synced songs, delete 4 →
     sync. With 4 synced songs, delete all 4 → sync.
  **Expected:** 6 of 12: no question (not more than half). 7 of 12: asks. 4 of 5: asks. All 4 of 4: asks (all of
  them). Check the Hungarian wording once.
  <sub>[sync.md][r4-38][r4-46]</sub>

- [ ] **SYNC-034** 🆕 (P0) A library folder gone from this device stops the run instead of emptying the cloud
  1. Desktop A with 12 synced songs: quit Campfire, move `library/songs` out of the data folder (keep `setlists`),
     start Campfire.
  2. Read the question; look at dropbox.com.
  3. Answer **Keep them and download**.
  4. Repeat 1; answer **Delete them from the cloud too**.
  5. IOS: delete the `songs` folder via the Files app (On My iPhone → Campfire → library), reopen Campfire.
  6. A library with only 2 synced songs, both deleted from outside the app: start.
  **Expected:** 1–2: the launch sync stops with "12 of your 12 synced files are gone from this device. Delete them from
  the cloud folder too, or keep them and download them again?"; **Dropbox still holds every song**; B is untouched.
  3: the songs come back onto A; Dropbox unchanged. 4: the cloud folder empties (and B loses them on its next run —
  which is then SYNC-032's question on B, since that is most of B's library). 5: IOS asks the same. 6: it asks even
  though only 2 are gone, because the library is empty. Hungarian sentences and buttons correct.
  <sub>[r5-01][sync.md]</sub>

- [ ] **SYNC-035** 🆕 (P0) A library folder replaced with a different one is not taken for mass deletions
  1. Desktop A with 12 synced songs: quit, move `library/songs` away and put 3 unrelated songs in its place (a restored
     old backup, a folder copied from another computer). Start A.
  2. Answer **Keep them and download**.
  **Expected:** 1: the run asks the "12 of your 12 synced files are gone from this device" question before anything
  moves; Dropbox still holds all 12. 2: the 12 come back onto A and the 3 unrelated songs go up as new files — the
  library ends with 15 on both sides. Answering the question about this direction does not waive the other: if B
  afterwards empties the cloud folder, A's next run still asks SYNC-032's question.
  <sub>[r5-01][sync.md]</sub>

- [ ] **SYNC-036** (P1) Answering twice is one run
  1. Get the question (SYNC-032), double-tap "Keep them and upload" (and on another run "Delete them here too").
  **Expected:** one run, not stopped, not repeated.
  <sub>[r3-13]</sub>

- [ ] **SYNC-037** (P0) Reconnecting after a refusal keeps the deletions; after a Disconnect it compares by content
  1. Delete 2 songs on A, sync. Revoke Campfire in the Dropbox account settings; Sync now on A (the section asks to
     connect again); Connect the same account; sync.
  2. Delete 2 more songs on A, sync. Disconnect A. While disconnected delete another song on A. Connect again, sync.
  **Expected:** 1: the 2 songs stay deleted everywhere (Connect keeps the index for the same account). 2: the 2 synced
  deletions stay deleted; the song deleted while disconnected comes back from Dropbox — Disconnect deletes the index,
  so the first run after it compares by content and a file only one side has is new rather than deleted. Nothing is
  lost in either case.
  <sub>[r4-01][repo-impl][sync.md]</sub>

## 4. Conflicts

- [ ] **SYNC-040** (P0) Changed on both sides → both kept, never merged
  1. Sync song X to A and B. Edit X differently on A and on B. Sync A, then B, then A.
  **Expected:** B keeps its own text under `x.cho` and gets A's as `x (2).cho`; after the last run A and Dropbox hold
  the same two files. The summary says "Kept both versions of a song that changed in two places: x (2).cho".
  <sub>[sync.md]</sub>

- [ ] **SYNC-041** (P1) A second conflict on the same song
  1. After SYNC-040, without syncing A, edit `x.cho` on A again; sync A.
  **Expected:** A and Dropbox hold three files: A's second edit as `x.cho`, and the two other versions as copies —
  nothing lost.
  <sub>[r2-09]</sub>

- [ ] **SYNC-042** (P1) Ten conflicts at once are summarised
  1. Take B offline, change ten songs differently on A and B, sync A, bring B online, sync B. Repeat with exactly one.
  **Expected:** "Kept both versions of 10 songs that changed in two places. Among them: …" naming a few; with one,
  the singular sentence with its name. Hungarian too.
  <sub>[r3-38]</sub>

- [ ] **SYNC-043** 🆕 (P0) Saving a song while a run downloads it keeps the saved text
  1. On B (or dropbox.com), change a long song X; sync B.
  2. On A, turn on "Very Bad Network". Press Sync now; while "Syncing…" shows, open X in the editor, change a word, and
     Save before the run finishes.
  3. Let the run finish; sync again.
  **Expected:** X on A still has the word you saved. B's version arrives next to it as `x (2).cho` (a kept-both
  conflict). After the second run both files are on Dropbox and on B. Never: A's saved word silently replaced by
  B's text. Repeat once with a tag toggle on X instead of an editor save, and once with a setlist edit while the run
  downloads that setlist.
  <sub>[r3-03][follow-up: sync save race]</sub>

- [ ] **SYNC-044** 🆕 (P0) Saving a song while a run deletes it keeps the song
  1. Delete song Y on B, sync B.
  2. On A with "Very Bad Network", Sync now; while it runs, open Y and save an edit.
  **Expected:** Y survives on A with the edit, and goes back up to Dropbox (an edit beats a deletion) — it is not
  deleted from under the save.
  <sub>[follow-up: sync save race]</sub>

- [ ] **SYNC-045** 🆕 (P1) A file changed on the other side through every pass is reported, not "synced successfully"
  1. On A, edit song Z. On the other side, keep rewriting `songs/z.cho` every half second for a minute (the API loop
     in section 7 — this is impractical by hand).
  2. Sync now on A while the loop runs.
  **Expected:** the run ends with "One file could not be synced: z.cho" (or a kept-both line plus that); "Last synced
  successfully on …" **does not move**. Stop the loop, Sync now: it settles (Z plus a ` (2)` copy) and the date moves.
  <sub>[follow-up: exhausted retries]</sub>

- [ ] **SYNC-046** (P1) A conflict copy that cannot be written loses nothing
  1. Desktop: sync, edit X locally and on dropbox.com, `chmod a-w library/songs`, Sync now.
  2. `chmod u+w library/songs`, sync.
  **Expected:** 1: X named as not synced; Dropbox still holds the web edit. 2: local text under `x.cho`, web edit as
  `x (2).cho` on both sides.
  <sub>[r2-10][r1-14]</sub>

## 5. Stopping, interruptions, failures

- [ ] **SYNC-050** (P0) Stop halfway, then finish
  1. A first sync of 50+ songs on MAC; press Stop syncing halfway.
  2. Sync now.
  **Expected:** 1: stops at once (a stopped outcome, not a network failure); `sync-index.json` lists what moved and
  `"isRunInProgress": false`. 2: no ` (2)` copies; only the rest moves. A restart does not report "interrupted" and
  does run its launch sync.
  <sub>[r1-01][r2-07]</sub>

- [ ] **SYNC-051** (P0) A killed run is reported next time and not restarted by itself
  1. During a long run, kill the app: desktop (`kill -9` / Task Manager), AND (`adb shell am force-stop` or swipe away),
     IOS (swipe away in the switcher).
  2. Start it again.
  **Expected:** "The last sync was stopped before it finished. Whatever had already been transferred was kept." No
  automatic run on that start; Sync now completes it with no duplicates.
  <sub>[r1-13][r2-17][r3-28]</sub>

- [ ] **SYNC-052** (P1) The network drops mid-run
  1. ~40 new songs on Dropbox; Sync now on A and cut the network a third of the way in.
  2. Network back; Sync now.
  **Expected:** 1: "Could not reach the service…"; the song list shows every file that did arrive. 2: the rest arrive.
  <sub>[r2-08][r2-07]</sub>

- [ ] **SYNC-053** (P1) Per-file failures are named and hold back "Last synced"
  1. Desktop: `chmod a-w library/songs`, add two songs on dropbox.com (one named `100% sure.cho`), Sync now.
  2. `chmod u+w library/songs`, Sync now.
  **Expected:** 1: "2 files could not be synced. Among them: …" with both names intact (the `%` survives); the date
  does not move. 2: the date line is back, newer. Hungarian too.
  <sub>[r2-16][r3-38]</sub>

- [ ] **SYNC-054** (P1) Storage problems
  1. Desktop: `chmod 000 preferences/sync-index.json`, Sync now; `chmod 644`, Sync now; restart with it unreadable.
  2. A 600 MB local `.cho` in the library: Sync now.
  **Expected:** 1: "The library could not be read or written."; nothing downloaded; the index byte-identical after
  `chmod 644`; the next run is ordinary; with it unreadable at start the account still shows. 2: the huge file is
  named as not synced and never uploaded.
  <sub>[r4-03][r3-06]</sub>

- [ ] **SYNC-055** (P2) Hammering the buttons
  1. Tap Sync now twenty times fast; tap Stop twice.
  **Expected:** Sync now is disabled during a run and Stop sits inside the progress row; no crash; Stop stops and does
  not restart.
  <sub>[r3-13][presentation]</sub>

## 6. A scripted pass (all five devices, about an hour)

Run in this order after sections 1–5, or on its own as a regression pass before a release:

1. Empty `Apps/Campfire`. MAC: clear library → demo library planted. Connect MAC → Dropbox gets the 2 demo songs +
   setlist.
2. Connect WEB (fresh site data: demo planted) → no duplicates, no ` (2)` (same content).
3. Connect AND and IOS the same way → still exactly 2 songs + 1 setlist on Dropbox.
4. On WIN import fixture: the few-hundred-song zip, connect, sync → others sync → identical counts everywhere.
5. Edit the same song on AND and IOS offline; bring both online; sync AND, IOS, AND → one ` (2)`, the same on all.
6. Rename a song on MAC; edit the old name on WEB before syncing it → both files everywhere.
7. Delete 3 songs on IOS → gone everywhere.
8. SYNC-032 on WEB, SYNC-034 on MAC (Keep answers) → nothing lost anywhere.
9. SYNC-043 on MAC.
10. Upload fixture: the Windows-illegal-names folder → WIN names them, others hold them.
11. Final: Sync now on every device twice; the second run of each plans nothing; the file counts match on every
    device and Dropbox.

## 7. Claude-run on this Mac

Once given the throwaway account, Claude can run most of sections 2–5 on this Mac by itself, with up to five
"devices" on one machine. What it needs from the user is at the end.

**The devices Claude can drive:**

- **Two desktop installations at once.** Each is the desktop app with its own `user.home` (the data folder and the
  single-instance lock are both derived from it, so two homes are two independent installations): build once with
  `./gradlew :app:desktop:createDistributable` and start `…/build/compose/binaries/main/app/Campfire.app/Contents/MacOS/Campfire`
  with `JAVA_TOOL_OPTIONS=-Duser.home=<scratchpad>/homeA` (and `homeB`), or temporarily add
  `jvmArgs("-Duser.home=…")` to `app/desktop/build.gradle.kts` for `:app:desktop:run` and revert it afterwards.
  Libraries and `preferences/sync-index.json` are then plain files Claude reads and seeds directly. Clicks are
  unreliable from a session, so anything that has to be pressed is done by a temporary in-app driver (a
  `LaunchedEffect` calling the view model's sync, delete, save and answer functions and writing marker files), with
  window-only screenshots (`screencapture -l <window id>`, app activated just before each capture) as the second
  check. Desktop A and B connect **one after the other**: the loopback redirect uses the fixed port 53682.
- **The web build** on the dev server in the Claude-in-Chrome tab (with the `requestAnimationFrame` override the
  hidden tab needs), its OPFS library read and written from the console.
- **The iOS simulator** (built with the `-target iosApp` recipe, installed with `simctl`), its library in the app
  container, taps through a CGEvent tool gated on the DeviceHub window being frontmost. A reinstall on the simulator is
  a reasonable stand-in for SYNC-006 if the simulator's keychain survives the uninstall the way a device's does; the
  run says which it observed.
- **The Android emulator** (`.debug` build via `adb install`), its library seeded as root (`adb root`, copy,
  `chown`), read with `run-as`, driven with `adb shell input`, killed with `am force-stop`.
- **"dropbox.com" as a device**, through the Dropbox HTTP API rather than the web UI: desktop A's
  `preferences/sync-credentials.json` holds a refresh token; exchanging it for an access token needs only the app key
  (`POST https://api.dropboxapi.com/oauth2/token` with `grant_type=refresh_token`, `refresh_token`, `client_id`), after
  which `files/list_folder`, `files/upload`, `files/delete_v2` and `files/move_v2` on `/songs` and `/setlists` (paths
  are relative to the app folder) let Claude empty the folder, upload the fixtures (decomposed names, illegal names,
  8 MiB+ files), and run SYNC-045's rewrite loop. The token stays in the scratchpad, is never written into the
  repository or a log, and the account is wiped afterwards. The Dropbox web UI in Chrome is the fallback.

**Order of runs:** (1) build all four targets with the app key; (2) connect desktop A (user approves), then desktop B,
web, simulator, emulator; (3) SYNC-010–012, 015–019 across desktop A/B and web; (4) SYNC-013 and SYNC-014 via API
uploads (SYNC-014 only as far as "the Mac receives them" — the Windows half needs WIN); (5) section 3 in order on
desktop A with desktop B as the other device — **SYNC-034/035 first on the scratch homes only**; (6) section 4,
including SYNC-043/044 with a slowed network (Network Link Conditioner needs the user to switch it on, or a delay
patched into the provider for that run) and SYNC-045 with the API loop; (7) section 5 with `kill -9`, `am
force-stop` and `simctl terminate`; (8) the scripted pass of section 6 minus WIN; (9) disconnect everything, empty
the app folder, report.

**What still needs the user's hands:**

- Logging in to Dropbox on each consent page (password, 2FA); Claude can press Allow in Chrome but should not type
  the password, and the desktop app opens the system's default browser, outside Claude's tab group.
- The Windows machine (SYNC-014's Windows half, the WIN column of section 6), a real iPhone (SYNC-006 on hardware, the
  Files-app deletion of SYNC-034, background runs), a real Android phone's backup restore.
- Network Link Conditioner (a System Settings pane Claude should not toggle on its own) and changing the system clock
  (SYNC-004).
- Revoking the app in the Dropbox account settings (SYNC-003), unless the user is happy for Claude to do it in the
  logged-in Chrome session.
