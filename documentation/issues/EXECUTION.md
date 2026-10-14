<!--
  This file is part of Campfire.
  Copyright (c) Pandula Péter 2017-2026.
  https://github.com/pandulapeter/campfire

  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  https://mozilla.org/MPL/2.0/.
-->
# Executing the thirty-first review's plans

Follow the `codebase-review` skill's section 4 with these specifics.

## Preconditions

Stop and tell the user if any fails: the tree is clean apart from `documentation/issues/`; the README records the
challenge; the Decisions section has the user's answer; the unit tests pass. Commit the plans if untracked
(`Add the thirty-first review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans, in order |
|---|---|---|
| S | `../Campfire-lane-s` | 09 |
| V | `../Campfire-lane-v` | 01 |
| U | `../Campfire-lane-u` | 02, 03, 04, 05, 08, 07 |

`git worktree add --detach ../Campfire-lane-<x> $START`. At most two lanes build at once: start S and V; start U when
either finishes. `local.properties` is not needed.

## Subagent prompt (fill in `<worktree>`)

> You carry out review plans in the Campfire repo, in the worktree `<worktree>` only. Every command starts with
> `cd <worktree> &&` or uses `git -C <worktree>`. Scratch files go to `<scratchpad>/lane-<x>/` only; never run a
> script you did not write; never `git stash`; never branch, merge, rebase, push, bump the version, or dispatch a
> workflow. Read the root `CLAUDE.md` and the `CLAUDE.md` nearest each file you touch.
>
> Plans, in order: `<list>` in `documentation/issues/`. For each:
> 1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking the recommended option; re-locate moved code by the quoted snippet. If the plan
>    is wrong, impossible, or its "drop this plan if" holds, revert the tree, leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files it names.
> 4. Verify (below). Run `python3 .github/scripts/check_sizes.py` and `spotlessCheck`; they must pass.
> 5. `git rm` the plan file in the same commit.
> 6. Load `commit-messages`; commit with one `-m` sentence. Check
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1"` and that the message has no
>    attribution; `git show --stat HEAD` touches only the plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final test result.

## Verification

- Lane S: `./gradlew :data:sync:implementation:desktopTest spotlessCheck`.
- Lane V: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:android:compileDebugKotlin
  :app:web:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64 :tools:screenshots:test spotlessCheck`.
- Lane U: `./gradlew :tools:screenshots:test spotlessCheck` (goldens are compared only on Linux; a new test must pass on
  the Mac and must not need a golden).

## Merging

In order **S, V, U**: list each lane's touched paths (`git -C <worktree> log --stat --format=%s $START..HEAD`) and stop
on any outside its lane (README Lanes table); then `git cherry-pick $START..<lane HEAD>` in the main checkout. On a
`size_allowances.json` conflict, take either side and rerun `check_sizes.py --update`, amending nothing: commit the
result as `Lower the size allowances after the merge.` only if it changed. Run the tests of the lane's modules after
each lane. After U, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
(run the web distribution alone if the combined build runs out of memory). Then `git worktree remove` each lane.

## Finish

`git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0; one
commit per plan; `git status` clean; `git worktree list` shows only the checkout. Update the `31st-review` memory. With
no plan file left, delete the folder and commit `Remove the review plans.` Do not push.
