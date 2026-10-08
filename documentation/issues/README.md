<!--
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
  distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

# Twenty-third review: refactor regressions

Reviewed at `c183cdb85` on `master`, 2026-10-08. **Angle:** did the 230 structure/SOLID commits of the twenty-second
review (`2940b0e0a..c183cdb85`: 80 simple refactors, then 148 plan commits) preserve behavior? **Budget:** standard:
five area reviewers, verification by the orchestrator, one challenger.

The working tree had one uncommitted change, the user's own `documentation/TO_DO.md`. No plan touches it.

## Headlines

- **No behavior regression in the app.** Five reviewers compared every move and split, line by line, against what it
  removed. That covered `:chordpro`, `:metronome`, `:domain`, all of `data/`, the view-model holders, navigation, the
  platform source sets, every screen and component, the strings, the shells, the build logic and the workflows. Two of
  them did it mechanically, with scripts that diff the multiset of removed and added lines.
- **One CI check went soft** (01): the extracted desktop release start check no longer fails when the log it greps or
  the class-load log is missing, since the script lost the `-e` GitHub's inline steps run with. A Windows build that
  throws at start could be submitted.
- The rest are low: a test of the repository holding the library lock was lost in the sync module split (02), one
  module missed the `campfire-koin` move (03), and stale names in nine places in the notes (04).

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Fail the release start check when its logs are missing | medium | C |
| 02 | Test that the repositories take the library lock | low | D |
| 03 | Apply `campfire-koin` to `:data:sync:implementation` | low | D |
| 04 | Fix the names the notes give for moved code | low | E |

## Lanes

| Lane | Area | Plans | Files owned |
|---|---|---|---|
| D | data | 02, 03 | `data/repository/implementation/src/commonTest/**`, `data/sync/implementation/src/commonTest/.../SyncEngineTest.kt`, `data/sync/implementation/build.gradle.kts` |
| C | CI | 01 | `.github/scripts/start_release_build.sh`, `.github/CLAUDE.md` |
| E | docs | 04 | `app/di/CLAUDE.md`, `app/web/CLAUDE.md`, `data/sync/implementation/CLAUDE.md`, `LibraryChanges.kt`, `presentation/.../ui/CLAUDE.md`, `ui/dialogs/CLAUDE.md`, `ui/navigation/CLAUDE.md` |

**Merge order:** D, C, E. D is the only lane with code under test. E touches only notes, so it goes last. No file is
shared between lanes.

## Challenge

One fresh challenger read every plan against HEAD and simulated plan 01's script under `/bin/bash` 3.2, including the
Windows path, which it drove through `tasklist` / `taskkill` shims. It amended 01, 02 and 04, found 03 sound, and
dropped none.
- **01:** the plan named two placements for the log guard. It now names one: after the post-wait `is_running`, before
  the exception grep. It also records that the guard only bites on Windows.
- **02:** the setlist helper is `writing`, reached through `saveSetlist`, not `withWriteLocks`. The plan now carries
  the exact test skeleton.
- **04:** every row now carries its exact replacement text.
  - The navigation note's premise was wrong, since nothing picks the editor out of a scene. It is now reworded to
    describe the editor's prefixed `contentKey`.
  - The sync notification effect is `rememberSyncNotifications`, and the next sentence's rescan belongs to
    `CampfireApp`.
  - The web loading screen's colors come from `CampfireColorScheme`, and its two-frame wait is in `CampfireApp.kt`.

## Decisions

None open. Every plan takes its recommended option.

## Checked and found solid

- **`:chordpro`:** the line scanner adoption in the parser, summary, highlighter, chord rewrite and splitter. The
  `MetadataKind` table and the object initialization order behind it. The chord name visitor. Every pure move. The
  `explicitApi` narrowing (no outside callers).
- **`:metronome`:** `MetronomeEngine`, `AudioClock` and `PlaybackGapTracker`, which keep the exact old expressions.
- **`:domain`:** the `ImportRun` / `ImportTriage` split, `SongCatalog` / `SongSorting`, and the logger injection.
- **`data/`, persisted formats and sync:**
  - No `@SerialName`, file name or JSON setting changed.
  - The sync repository split keeps the run mutex, the restore mutex, the debounce, the throttle, the `NonCancellable`
    sections and the scopes.
  - The `DeletionGuard`, `PassListing`, `ConflictResolver` and sealed-exception moves are verbatim.
- **`data/`, other layers:**
  - Every split-out sync class is `@Single`, and no Koin definition has a defaulted parameter.
  - The Dropbox retries and token renewal, the shared retry wait, `WholeDocumentRepository`, the JVM storage merge, the
    OPFS and PDF splits, and the name identity rule are all unchanged.
- **The view model:** every holder was compared with the code it took from the view model. The `init` order is
  unchanged, every state is still `Eagerly` with the same initial values, and the navigator and back stack listeners
  run in the old order. The shortcut table, `BrowserRoutes` and `CampfireWebApp` were checked too.
- **UI:** all 52 split and move commits in `2940b0e0a..94d4b7fcd` are line-multiset neutral, and the order check found
  nothing. The plan commits in the UI are equivalent. Every string is read through the in-app localization, and the en
  and hu key sets match.
- **Workflows:** every publish workflow passes the same secrets. `write_local_properties.py` reproduces the old
  `printf` output byte for byte. CI still runs every test it ran before, and more.
- **Build logic and packaging:** the build logic tasks equal the removed script code, and the configuration cache
  stores entries for the release tasks. The ProGuard keeps and the manifest components still resolve.
- **Entry points and backup:** the entry points are equivalent. The Android backup allow-list and the iOS backup
  exclusions still match the real paths.

## Dropped after verification

- **The editor captures the notation when its follow-file and revert effects start** (511077396). This is harmless:
  the notation cannot change while the editor is on the stack. It is not synced, and Settings is only reached by
  replacing the stack.
- **The `:data:formats` extractor tests call a copy of `extract`.** The copy is documented in the module's notes, it
  matches the real method today, and `DocumentGoldenTest` covers the real method end to end.
- **61 Baseline Profile rules name classes from before the move.** This was already known, and stale rules are only
  ignored. The prepare-release skill records the profile again. Owed before release, see below.
- **`PrepareImportUseCaseImpl` still imports `normalizedToNfc`.** It is still used.

## Manual checks owed

- 01: dispatch `publish-linux.yml` and `publish-windows.yml` with `submit` off. The start step should pass and print
  the class sharing line.
- Before release: regenerate the Baseline Profile (`:app:android:generateBaselineProfile`).
