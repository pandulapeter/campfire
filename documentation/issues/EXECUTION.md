<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Executing the review plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It is the
`codebase-review` skill's section 4 for this sweep; where they differ, the skill wins.

## Preconditions (stop and tell the user if one fails)

- The tree is clean apart from this folder; the plans are committed (`Add the <nth> review plans.` if they are not).
- Every plan carries its challenge outcome (README, "Challenged"); a plan marked dropped is deleted, not run.
- Every decision in the README has an answer. Decisions 1 and 2 are not plans; carry out whatever the user answered as one
  commit each before the lanes start.
- The unit tests pass (root `CLAUDE.md`, Tests). Then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree |
|---|---|---|
| A | 03, 01, 02, 04, 05, 13 | `../campfire-lane-a` |
| B | 06, 07 | `../campfire-lane-b` |
| C | 08, 09 | `../campfire-lane-c` |
| D | 10, 11, 12, 16 | `../campfire-lane-d` (after B merged if plan 10 touches `ui/songView/`) |
| E | 14, 15 | `../campfire-lane-e` |

`git worktree add --detach ../campfire-lane-<x> $START` per lane; at most two Gradle builds at once, so start B and E,
then D and C, then A. Plan 09 needs an emulator: if none is available, the lane commits the generator change only and
reports the re-record as not done.

## Subagent prompt (fill in `<worktree>`, `<x>` and the plan list)

> You carry out review plans in the Campfire worktree `<worktree>`, in this order: `<plans>`. Every command starts with
> `cd <worktree> &&` or uses `git -C <worktree>`; scratch files go to `<scratchpad>/lane-<x>/` only, and you never run a
> script you did not write. Never `git stash`, branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> For each plan: read it and the files it names; load the `code-style` skill before the first edit; implement exactly
> the plan with its recommended option, finding moved code by the quoted snippets. If the plan is wrong, impossible, or
> its "drop this plan if" condition holds, revert the tree, leave the plan file, note why, and go on. Add the tests it
> asks for; update the `CLAUDE.md` files it names. Verify with the commands below. `git rm` the plan file in the same
> commit. Load `commit-messages` and commit with one `-m` sentence, then check:
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
> amend on the spot if either prints. `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
> then the final test result.

## Verification

- `./gradlew :presentation:desktopTest` and `:app:desktop:compileKotlin` for every plan.
- `androidMain` touched → `:app:android:compileDebugKotlin`; `wasmJsMain` or plan 10 → `:app:web:compileKotlinWasmJs`;
  `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
- Plans 01, 06, 07, 08 → `./gradlew :tools:screenshots:test` (goldens must not change).
- Plan 09 → `./gradlew :app:android:generateBaselineProfile` on an emulator.
- `python3 .github/scripts/check_sizes.py --base $START` and `python3 .github/scripts/check_license_headers.py`.

## Merging

In the order **B, E, D, C, A**: list each lane's touched paths
(`git -C <worktree> log --stat --format=%s <base>..HEAD`) and stop on any outside the lane's files; then
`git cherry-pick <base>..<lane HEAD>` in the main checkout. `CLAUDE.md` conflicts are merged word by word, keeping both
sides. Run the unit tests after each lane and the full build after the last:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. Then `git worktree remove ../campfire-lane-<x>`.

## Finish

`git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
`git log --oneline $START..HEAD` is one line per plan; `git status` is clean; `git worktree list` shows only the checkout.
Report the commit count and the skipped plans with reasons. When only `README.md` and `EXECUTION.md` are left, delete the
folder and commit `Remove the review plans.`; otherwise leave it for the user. Do not push.
