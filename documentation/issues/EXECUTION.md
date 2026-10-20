<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 41st review plans

The brief for the orchestrating agent. The plans are in this folder; `README.md` indexes them and records the
decisions. Load the `codebase-review` skill first: its section 4 is the procedure this file instantiates.

## Preconditions (stop and tell the user if any fails)

- `git status` is clean apart from this folder. The plans re-probe their charts with
  `chordpro/src/desktopTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ProbeSongsTest.kt`
  (`PROBE_CORPUS=<file> ./gradlew :chordpro:desktopTest --tests "*ProbeSongsTest*" --rerun`).
- The challenge was waived by the user (`README.md`, "Challenge"); each lane agent reads a plan's fix against its
  lane's other plans before starting it.
- Every open decision in `README.md` has an answer recorded there.
- `./gradlew :chordpro:desktopTest :presentation:desktopTest -q` passes.
- Commit the plans if they are untracked: `Add the 41st review plans.` Then `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane next to the checkout, cut from `START`
(`git worktree add --detach ../Campfire-lane-<x> START`), except lane U, which is cut from the main checkout's `HEAD`
after lanes G and S have merged (its plan 09 reads the engine's groups and plan 14 the rows plan 10 changes).

| Lane | Plans, in execution order | Files owned |
|---|---|---|
| G | 01, 02, 03, 04, 05, 07, 08, 10, 11 | `chordpro/.../scales/SongScaleGroups.kt`, `SongParts.kt`, `PlayableScales.kt`, `SongScaleGroupsTest.kt`, `PlayableScalesTest.kt` |
| S | 06, 12, 13 | `chordpro/.../scales/ScaleExceptions.kt`, `SongScaleSuggestions.kt`, `ScaleExceptionsTest.kt`, `SongScaleSuggestionsTest.kt` |
| U | 09, 14, 15 | `presentation/.../ui/songView/model/SongScaleGroups.kt` and its test, the songView `CLAUDE.md`, `chordpro/.../scales/Scales.kt`, `ScalesTest.kt`, both `strings.xml`, `presentation/.../ui/scales/ScaleNames.kt` |

Shared file: `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md` — every lane may
add or amend the lines its plans name; merge conflicts there are resolved as a word-level three-way merge, every
sentence from both sides kept.

**Merge order: G, then S, then U.** G first because S's plan 13 shows its value only once G's plan 01 lands, and U
last because its plan 09 names the parts the engine (G, S) still drops and its plan 14 cuts the rows G's plan 10
reorders.

At most two Gradle builds at once: run lanes G and S together, then U.

## The subagent prompt (one `general-purpose` subagent per lane, all confined to their worktree)

> You execute lane <X> of the plans in `documentation/issues/` of the Campfire repository, in the worktree
> `<absolute worktree path>` only. Every command starts with `cd <absolute worktree path> &&` or uses
> `git -C <absolute worktree path>`. Scratch files go to `<scratchpad>/lane-<x>/` only; never run a script you did not
> write; never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> For each plan, in this order: <plan files of the lane>:
> 1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking the recommended option (the README's "Decisions" section records the user's
>    answers where a plan offered a choice). Re-locate moved code by the quoted snippet. If the plan is wrong,
>    impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert the working tree,
>    leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` lines and strings it names. A file already past its size limit
>    may grow; pushing one past it is refused by the pre-commit hook (code-style skill).
> 4. Verify: `./gradlew :chordpro:desktopTest -q` (lane U also `:presentation:desktopTest`) and read the result from
>    `build/test-results/desktopTest/*.xml` (zero failures), plus `./gradlew :chordpro:spotlessCheck -q` (lane U also
>    `:presentation:spotlessCheck`) and `./gradlew :app:desktop:compileKotlin -q`.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill; commit with a single `-m` sentence — one fix, one commit. Then check the raw
>    commit object and amend on the spot if it fails:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`.
> 7. After every commit, `git show --stat HEAD` must list only that plan's files (plus the plan file removed).
>
> Lane report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then
> `Not done: NN — <why>`, then the final test result.

Fill in `<X>`, `<absolute worktree path>`, `<scratchpad>` (this session's scratchpad directory), and the lane's plan
files.

## Merging

In the README's order, as lanes report: first `git -C <worktree> log --stat --format=%s START..HEAD` and stop on any
path outside the lane's files (the scales `CLAUDE.md` excepted); then in the main checkout
`git cherry-pick <base>..$(git -C <worktree> rev-parse HEAD)` (take the hash from the worktree, not from the lane's
report). Resolve conflicts keeping both sides' intent. After each lane: `./gradlew :chordpro:desktopTest
:presentation:desktopTest -q`; after the last: the full build
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS`
and, on its own, `./gradlew :app:web:wasmJsBrowserDistribution`. A break is fixed by one more commit, never by
rewriting landed ones. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

Plan files still present are the skipped ones: leave them with the README trimmed to them, or ask the user. Final
checks: `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
`git log --oneline START..HEAD` is one line per plan (plus any break fix); `git status` is clean; `git worktree list`
shows only the checkout. Report the commit count and the skipped plans with reasons. Update the memory note
`41st-review.md`. When no plan file is left, delete this folder and commit it as `Remove the review plans.` without
asking. Do not push.
