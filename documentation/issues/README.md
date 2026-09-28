# Review of 2026-09-28 (at `cc4d6f55`)

A read-only review of the whole app. It concentrated on what has arrived since v4.5.0 and had not been reviewed yet:
cover art, song links, automatic sync after edits, and the desktop title bar and editor toolbar fixes. It also covered
UI performance. Seven area reviewers each verified their findings against the code, and one duplicate was merged
(the sync reviewer's "chained run loses the keep-alive" is plan 03). None of the plans has been executed.

The reviewers were read-only: no source was changed and Gradle was not run. Much of the reading was done file by file,
because a tool outage cut off their shell access for part of the run.

Each plan is self-contained (Problem / Fix / Verification / Conflicts / Open decision) and lands as its own commit.

## Headlines

- **17 — the editor can write an old version back over a newer file, and the automatic sync then uploads it.** This
  happens when an editor with nothing typed in it is open while its file changes underneath it (from sync, another
  program, or the Files app). Save becomes enabled, Back asks about "unsaved" changes, and saving reverts the file.
  Since cc4d6f55 that revert reaches every device. It is the one data-loss finding.
- **02 / 03 — on Android and iOS, the keep-alive often misses automatic runs.** It misses the run that leaving the
  app starts (ON_STOP comes before the composition hands it over), and a run chained right behind another. Such a
  run is then killed in the background, so the next launch reports "interrupted" and does not sync (01).
- **10 / 13 — untrusted `{meta: link}` addresses.** A backslash lets a link chip show one host and open another (10).
  On the web, links open without `noopener`, so the opened page can replace the Campfire tab (13).
- **14 — cover downloads are unbounded and are never abandoned.** A fling through a library whose covers are not yet
  cached queues hundreds of requests. The covers on screen then time out and stay blank for a minute.

## Lanes

The lanes run in parallel; within a lane, run the plans in the order listed. **Merge order: B, C, D, A, E.** A and E
both touch `CampfireApp.kt` and `CampfireViewModel.kt`, in different blocks.

| Lane | Plans (in order) | Area |
|---|---|---|
| **A** — automatic sync lifecycle (serial: shared `SyncRepositoryImpl`, sync service, notifier) | 01, 02, 03, 04, 05 (06 rejected) | `:data:repository` sync, `CampfireApp.kt` sync effect, `:app:android` / `:app:ios` / `:app:desktop` |
| **B** — data | 07, 08, 09 | `BaseLocalDataRepository`, setlist local source, `ImportPlanner` |
| **C** — links | 10, 11, 12, 13 | `SongLyrics.kt` `linkLabel`, `ChordProLinks`, iOS and web URL opening |
| **D** — cover art | 14, 15, 16 | `CoverArtRepositoryImpl`, `CoverArtSearchSheet.kt` |
| **E** — screens | 17, 18, 19, 20, 21 | `SongEditorScreen.kt`, keyboard padding in `CampfireApp.kt`, search / picker in `CampfireViewModel.kt` and `Dialogs.kt` |

## Index

| # | Kind | Sev. | Platforms | Title |
|---|---|---|---|---|
| 01 | bug | low | android, ios, web | An interrupted automatic run suppresses the next launch's sync |
| 02 | bug | medium | android, ios | The run that leaving the app starts never reaches the platform keep-alive |
| 03 | bug | medium | android, ios | A chained automatic run loses the foreground service / background task |
| 04 | bug | low | android | A foreground-service notification appears after nearly every edit |
| 05 | bug | medium | desktop | Quitting drops the waiting automatic run or cuts the running one |
| 06 | performance | medium | all | Every automatic run re-hashes the whole library |
| 07 | performance | medium | all (ios, desktop) | A rescan re-reads itself for the whole of a sync run |
| 08 | bug | low | all | Editing only a setlist's description renames its file |
| 09 | performance | low | all (web) | Import reads older-named songs one by one |
| 10 | bug | medium | all | A backslash in a link spoofs the chip's host |
| 11 | bug | low | all | The link fallback accepts `mailto:`, `https:/x` and similar as addresses |
| 12 | bug | low | ios 15–16 | A link with unencoded characters does nothing when tapped |
| 13 | bug | medium | web | Links are opened without `noopener` |
| 14 | performance | medium | all | Bound cover downloads and drop the abandoned ones |
| 15 | bug | medium | all | The typed-address cover preview never loads (confirm on screen first) |
| 16 | bug | low | all | A tile that failed to load keeps its selection, and Save writes the dead address |
| 17 | bug | medium | all | An untouched editor goes stale when its file changes |
| 18 | ui-performance | low | android, ios | The editor recomposes on every keyboard frame |
| 19 | ui-performance | medium | ios (android?) | The song details reflow under a dialog's keyboard |
| 20 | bug | low | all | A symbol-only song search drops the sections and the scroller labels |
| 21 | ui-performance | low | all (web) | The song picker processes the whole library in its first frame |

## Decisions (taken 2026-09-28)

- **01:** A — a killed *automatic* run is neither reported nor allowed to stop the launch run.
- **03:** A — the shells wait about 2 s before letting go.
- **04:** A — the Android service runs only while the app is not in front.
- **05:** A — quitting waits (bounded at about 15 s, with the window hidden) for the run to finish.
- **06:** **rejected** — every run stays a full read, for the same reason plan 36 of the performance review was
  rejected. Do not execute 06; lane A ends at 05.
- **07:** A — one bounded re-read, then the changes that landed are replayed.
- **10:** the label is fixed; `ChordProSyntax.webUrl` is left unchanged.
- **17:** 1 — the editor follows its file silently while nothing has been typed.

## Execution notes

- Cap concurrent Gradle builds at two, since earlier runs hit Kotlin daemon OOMs with more. Each plan names its test
  task. Lane A's plans 02–05 also need emulator, simulator or desktop checks, which are described in the plans.
- Keep the module `CLAUDE.md` files in sync as each plan says. Lanes A and E both edit `presentation/CLAUDE.md`, so
  merge with care.
