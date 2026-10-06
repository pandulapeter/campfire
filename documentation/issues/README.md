# Nineteenth review: final pre-release review before 4.7.0

Reviewed at `3eaa29e24` (master). The working tree held the user's own uncommitted edit of `documentation/TO_DO.md`,
which no plan touches. The website repository (`../CampfireWebsite`) holds the user's own uncommitted work too.

**Angle:** what changes when 4.7.0 (191 commits since 4.6.1) reaches existing users and the stores: the upgrade from
4.6.1 and a library synced between a 4.6.1 and a 4.7.0 device; what only breaks in release builds (R8, ProGuard,
K/N release, Wasm production); store policy; the release pipeline and the documentation it relies on; the two commits
since the eighteenth review and a fresh install's first minutes; translations and crash paths. Six read-only
reviewers, one of them building and running the Android and desktop release artifacts. Every finding was checked
against the code before it became a plan; plans 01 and 10 were written by agents that probed them in a scratch
worktree.

## Headlines

- **Nothing release-blocking in the code.** The R8 release APK and the ProGuard desktop image were built and run with
  the new features (demo library, covers, chord diagrams, metronome in the background with its foreground service and
  media session): no exception, no missing class. The iOS release framework and the web production distribution build.
- **40** The published privacy policy says transpositions are never synced; from 4.7.0 they are, with tempo, capo and
  the chord shapes. Store policies require it to match — fix the website before the release.
- **20** The Windows start check's new log grep (added after 4.6.1, never run) may take Skiko's renderer fallback on a
  GPU-less runner for a crash and stop the Microsoft Store submission. The Linux leg already sets the fix.
- **01** A fresh install that connects to a Dropbox folder with an older version's untouched demo songs gets
  " (2)" copies of both on every device (the demos changed in 4.6.0 and again in 4.7.0).
- **10** A click at volume 0 or with every beat muted keeps iOS (background `audio` mode, guideline 2.5.4) and the
  Android media service running with nothing audible once the app is in the background.

## Index

| #  | Plan | Severity | Lane |
|----|------|----------|------|
| 01 | Take the cloud copy of an untouched demo song | medium | A |
| 10 | Stop a silent click when the app leaves the front | low | P |
| 20 | Render the Windows start check in software | medium | E |
| 21 | Declare the system boot time reason in the iOS privacy manifest | low | E |
| 22 | Drop the reference to the deleted release check | low | E |
| 30 | Take the unsaved changes dialog's focus from inside it | low | P |
| 31 | Say in the app's language that no app can open a link | low | P |
| 40 | Say in the privacy policy what sync carries besides the library | medium | W |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| A | demo library × sync | 01 | `data/*`, `domain/*`, `DemoLibrary.kt`, its parts of `CampfireViewModel.kt` |
| P | presentation, metronome, Android shell | 30, 31, 10 | `Dialogs.kt`, `CampfireMainActivity.kt`, `CampfireAndroidApp.kt`, `metronome/api`, `CampfireApp.kt`, its parts of `CampfireViewModel.kt` |
| E | CI, iOS manifest, docs | 20, 21, 22 | `.github/workflows/publish-windows.yml`, `PrivacyInfo.xcprivacy`, the document fixtures' README |
| W | website (another repository) | 40 | `../CampfireWebsite/privacy/index.html`, working tree only |

**Merge order: E, A, P.** E touches nothing the others do. A and P both edit `CampfireViewModel.kt`, the root
`CLAUDE.md` and `presentation/CLAUDE.md` in different places; P goes last because it also owns `strings.xml` and the
most UI files. W is done by the orchestrator itself, with no worktree and no commit in either repository.

**Shared files:** `CampfireViewModel.kt` (A: the demo planting paths; P/10: app start/stop handlers and a message),
root `CLAUDE.md` (A: Sync conflict bullet and the demo-library bullet; P/10: Metronome section), `presentation/CLAUDE.md`,
`strings.xml` ×2 (P only, plan 10). Conflicts are merged word by word, keeping both sides.

## Challenge

Ran on all eight plans with two fresh agents. **Amended:** 01 (takes the cloud copy only where the index has no entry
for the file, since an edit undone on this device would otherwise be dropped silently; removes the then unused
`SaveUserPreferencesUseCase` injection), 10 (3 s grace instead of 1 s, for an Android recreation on a slow phone;
Hungarian reworded; two cases left out on purpose), 21 (also updates the plist's comment and `app/ios/CLAUDE.md`),
31 (reuses the existing `Message.LinkNotOpened` snackbar the desktop already shows — no new string), 40 (a third
sentence, in *Deleting your data*, turns false too: the cloud `preferences.json` with the chord shapes stays).
**Sound:** 20, 22, 30. **Dropped:** none.

## Decisions

Answered by the user on 2026-10-06 (all three as recommended):

- **D-01** — **land it.** Land plan 01 before the release? It is the largest change of the sweep (about 20 files across every layer,
  with a new local preference), for a case that is already in 4.6.0 and that it only partly closes (an old installation
  that planted older demos is not covered). *Recommended: land it* — with its engine tests it is contained, and a fresh
  install on a user's second device is exactly the 4.7.0 launch moment.
- **D-10** — **land it.** Plan 10 stops a silent click (volume 0 or all beats muted) three seconds after the app leaves the front, on
  every platform, with a snackbar on return. *Recommended: land it.* Alternative: accept the 2.5.4 risk (low; a
  reviewer has to try that combination).
- **D-40** — **edit the working tree, uncommitted.** Plan 40 edits a file in the website repository that holds the user's own uncommitted work. *Recommended:
  apply the three text edits in its working tree, uncommitted, for the user to publish with their changes.*

## Manual steps owed before publishing (not code)

- **Play Console → App content → Foreground service permissions:** declare *Media playback* for the metronome (a
  description and a short video of the click on the lock screen with its notification). New since 4.6.1 with
  targetSdk 37; without it the Play release is blocked.
- **App Review notes** for the iOS / Mac `audio` background mode: the metronome keeps playing with the screen locked.
- Dispatch `publish-windows.yml` with `submit` off once plan 20 has landed (the sixteenth review's check 62).
- Publish the website with plan 40's edit.

## Checked and found solid

- Upgrade 4.6.1 → 4.7.0: every `preferences.json` field keeps its name/type, new ones default (the inverted
  `areChordsEnabled`, print settings' `showKey`/`showTempo` falling back to `showMetadata`, notation and palette ids);
  demo planting and credential forgetting are gated on a missing preferences document; What's new shows once.
- Mixed 4.6.1/4.7.0 sync: 4.6.1's setlist format ignores and keeps unknown fields (entry tempo/capo survive its edits);
  the cloud `preferences.json` is invisible to 4.6.1's engine and deletion guard; the index only gained a nullable field;
  ChordPro files are not rewritten on read.
- Release builds: no rule-file change needed (Ktor engines explicit, generated serializers, enums by id, no new
  reflection); dependencies unchanged since 4.6.1; the joined desktop jar keeps its service files.
- Store policy: the Android service is unexported, typed, started from the foreground, MediaStyle (exempt from
  `POST_NOTIFICATIONS`); iOS session handling, Now Playing (play disabled), no ATS exceptions, tracking false; Mac needs
  no audio entitlement; MSIX `runFullTrust`; donations hidden on Apple platforms.
- Release pipeline: tag/version and build number checks for 4.7.0 (48 > 47), `release_description.py` on the real
  CRLF 4.6.1 body, store scripts' retries, version metadata on every platform, the Linux/macOS start checks' paths,
  `metronome-timer.js` in the web build's digest list. Python (28) and Node (16) tests pass.
- Save shortcut: one action per press, never a Delete, never into the screen under a sheet, disabled buttons
  respected, web's Save page blocked; setlist chooser gated in performance mode, archived setlists and with Setlists off.
- First run: welcome once, What's new skipped, notification permission only for sync, demo cover from
  raw.githubusercontent.com served with CORS.
- Strings: both files 695 lines with identical keys, plurals complete, format specifiers matching, no unused key; no
  hard-coded English besides plan 31. Crash sweep of everything added since 4.6.1: nothing reachable.

## Dropped after verification

- A 4.6.1 peer still makes a " (2)" copy of a legacy undated setlist that a 4.7.0 device dated, if the 4.6.1 device
  edited it offline: nothing on the 4.6.1 side can change, and not writing the backfilled day would reverse D-20.
- The iOS release link ran out of heap in a warm Gradle daemon; it passed in a fresh one, which is what CI uses — a
  margin note, not a defect (raise `-Xmx` if a CI archive ever dies with "Java heap space").

## Manual checks owed

Each plan's own *Manual check*; the ones a release waits for: 20 (the Windows dispatch), 40 (published), and the
Play Console declaration above.
