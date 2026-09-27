<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Execution brief for the orchestrating agent

You are resolving the performance plans in this folder (`/Users/pandulapeter/Projects/Campfire/documentation/performance/`)
in parallel. Read `README.md` first, and the root `CLAUDE.md`. Each plan is self-contained: problem, fix,
verification, and commit message.

## 0. Preconditions

- `git -C /Users/pandulapeter/Projects/Campfire status --short` must be empty. If anything is uncommitted, stop
  and ask the user. Record `git rev-parse HEAD` as `START`. The plans are committed, so every worktree has them.
- **Decisions are taken** (see `README.md`), so don't ask again:
  - 10 is deferred: don't execute it.
  - 23 runs with its default.
  - 26 executes **option A (stepped lerp) only**.
  - 36 is rejected: don't execute it.
  - 38 runs without its optional fsync step.
  - 48 is approved and needs an emulator pass.
- **No branches.** The repo's rule is never to create a git branch without being asked. Lanes work in **detached
  worktrees**, and their commits are cherry-picked onto `master`.

## 1. Rules every lane agent must follow

Give each lane agent these rules verbatim, together with its lane's plan list:

1. Before editing any `.kt`, `.kts` or `strings.xml` file, invoke the `code-style` skill. Before committing,
   invoke the `commit-messages` skill.
2. Execute your plans **in the listed order, one commit per plan**, using the plan's commit message exactly: one
   line, no body, no `Co-Authored-By`, no other trailer. `git commit` only the files that plan changed.
3. Before editing, re-read the code the plan cites. Line numbers may have moved because of your own earlier
   commits. If a claim no longer holds, skip the plan and report why. Don't improvise a different fix.
4. Keep documented behavior. When a plan says "must not change", that is binding. Update any `CLAUDE.md` or
   KDoc that the plan names **in the same commit**.
5. After each plan, run that plan's verification. At minimum:
   - compile every touched module for desktop: `./gradlew :<module>:compileKotlinDesktop`
   - run the unit tests of every module with `commonTest` that the plan touches (the command is in the root
     `CLAUDE.md`)

   Before your last report, also compile the platforms your lane touched:
   - `:app:android:assembleDebug`
   - `:app:web:compileKotlinWasmJs`
   - `:app:ios:linkDebugFrameworkIosSimulatorArm64`
   - `:app:desktop:compileKotlin`
6. Don't push. Don't touch other lanes' plans. Don't edit files outside your plans' `Files` rows unless the
   compiler forces it; if it does, report it.
7. Report back with: the commit hashes against plan numbers, the skipped plans and why, the verification you ran
   and its result, and any manual checks you could not do.

## 2. Lanes

Create one detached worktree per lane from `START`:

```
git -C /Users/pandulapeter/Projects/Campfire worktree add --detach ../Campfire-perf-<lane> START
```

Copy `local.properties` into each worktree if one exists. Gradle builds are heavy, so run at most **three lanes'
Gradle builds at once**. Starting all six lanes is fine; they queue their builds.

| Lane | Area | Plans, in this order |
|---|---|---|
| A | Song details and editor preview | 02 → 01 → 03 → 04 → 05 → 08 → 07 → 11 → 06 → 09 |
| B | `:chordpro` and editor typing | 14 → 12 → 13 |
| C | List screens and view-model derived state | 15 → 16 → 17 → 18 → 27 → 19 → 20 → 21 → 22 |
| D | App shell, theme, rescans | 32 → 25 → 26 (option A) → 23 → 24 → 29 → 30 → 31 |
| E | Data layer and web storage | 33 → 34 → 41 → 35 → 39 → 37 → 38 (no fsync step) → 40 → 42 → 43 |
| F | Android and web shells | 49 → 45 → 46 → 47 → 48 |
| Phase 2 | Serial, on `master` after all merges | 28 → 44 |

Lane-specific notes to pass on:

- **A:** 02 must land first; 01, 03 and 04 use its API. 11 comes after 07 because it annotates the classes 07
  and 08 reshape. 11 also touches `gradle/build-logic`, so run a full `./gradlew :presentation:compileKotlinDesktop`
  and Android compile after it.
- **C:** 15 before 19 (both change `SectionHeader`'s signature). 27 builds on 18.
- **D:** 32 and 25 edit the same launch-screen block of `CampfireApp.kt`. 24 changes how invalidated song texts
  are re-read, and lane E's 37 changes what the invalidation events carry. Lane E merges first, so lane D adapts
  24 while rebasing (see §3).
- **E:** 33, 34 and 41 share `FileStorage.wasmJs.kt`. 35, 37 and 39 share `SyncRepositoryImpl.kt`. 37 and 38
  share the repository interfaces and test fakes. Web plans must be checked in a real browser; the recipe is in
  the user's memory note `web-target-verification.md`, under
  `/Users/pandulapeter/.claude-personal/projects/-Users-pandulapeter-Projects-Campfire/memory/`.
- **F:** 45 changes the Android window and splash colors. Screenshot a cold start in light and dark mode on the
  emulator before and after (see `android-emulator-verification.md` in the same memory folder). 46 starts by
  confirming the missing code cache, and is dropped if Chrome already caches. 48 must be exercised on the
  emulator (rotation, a freeform or split-screen resize, a density change) against every site the plan lists,
  before it is committed.
- **Phase 2:** 28 touches `CampfireApp.kt` and all the top-level screens, which is why it waits for every lane.
  44 records the Baseline Profile on the emulator, so it goes last to capture the final startup path.

## 3. Merging

Merge in this order, one lane at a time, by cherry-picking the lane's commits onto `master` in lane order:
**B → F → E → C → D → A**.

- On a conflict, resolve it by keeping both intents: the later lane's plan applied on top of the earlier lane's
  code. The hot spots are:
  - `CampfireViewModel.kt` (C: 18/27, D: 23/24, A: 01/02/07)
  - `CampfireApp.kt` (D)
  - `presentation/CLAUDE.md` (many)
  - `SongEditorScreen.kt` (A, and B only via `EditorToolbar.kt`)

  If a resolution needs more than mechanical merging, hand it back to that lane's agent together with the
  conflict.
- **Lane D, plan 24, after E's plan 37:** 37 makes `SongContentRepository` invalidations carry file names, with a
  single "everything" event above a threshold. 24's batched re-read must handle both kinds of event.
- After each lane is merged, run the full test command from the root `CLAUDE.md` plus `:app:android:assembleDebug`,
  `:app:desktop:compileKotlin`, `:app:web:compileKotlinWasmJs` and `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
  If they fail, fix before merging the next lane. A fix is its own commit, with a one-line message that names what
  it fixes.
- Then run phase 2 on `master` itself, with the same checks.
- Remove the worktrees: `git worktree remove ../Campfire-perf-<lane>`.

## 4. Final report to the user

- For every plan: its commit hash on `master`, or skipped with the reason.
- The decisions that were applied, and a reminder that 10 is waiting for the user to try pinching on a phone.
- The manual checks still owed, taken from each plan's verification section. Group them by platform (Android
  device/emulator, iOS simulator, desktop, web in Chrome and Safari). Include pinch-to-zoom smoothness on a
  low-end Android phone, a 2000-song library cold start on the web, and a sync run against a real Dropbox account
  (`sync-live-testing.md` in the memory folder).
- Whether `documentation/testing/release-check.md` needs an update. The root `CLAUDE.md` requires one when first
  run, importing, sync or the packaged builds change, and 23, 35, 37, 38 and 44 touch those.
- Leave `documentation/performance/` in place; the user decides when to delete it.
- Don't push.
