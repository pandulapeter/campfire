<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# Executing the 55th review plans

Follow the `codebase-review` skill's section 4; this is it, filled in for this sweep.

## Preconditions

Stop and tell the user if any fails: the tree is clean apart from `documentation/issues/`; the challenge ran (README,
"Challenge"); no decision is open; `./gradlew desktopTest` passes. Commit the plans if untracked
(`Add the 55th review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans in order |
|---|---|---|
| F | `../Campfire-lane-f` | 03 |
| P | `../Campfire-lane-p` | 02 |
| K | `../Campfire-lane-k` | 01 |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. At most two lanes build at once: start F and P
together, K when the first finishes. Lane F needs the import corpus: copy `local.properties` into its worktree (it
names `campfire.importCorpus`), and run the corpus before and after the change.

## Subagent prompt (one `general-purpose` agent per lane, model Opus)

> You carry out the Campfire review plan(s) `<plans>` from `documentation/issues/`, in the worktree `<worktree>` only.
> Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`. Scratch files go to
> `<scratchpad>/lane-<x>/` only; never run a script you did not write; never `git stash`; never branch, merge, rebase,
> push, bump the version, or dispatch a workflow.
>
> Per plan: read it and the files it names; load the `code-style` skill before the first edit. Implement exactly the
> plan, taking the recommended option; re-locate code by the quoted snippets. If the plan is wrong, impossible, or its
> "drop this plan if" condition holds, revert the working tree, leave the plan file, note why, and stop. Add the tests
> it asks for; run a test that pins a defect once without the fix and see it fail (say so only in your report). Update
> the `CLAUDE.md` files and strings it names. A file, function, or constructor past its size limit may not grow.
> Verify: the touched modules' `desktopTest`, plus the compiles below for every platform the change touches. `git rm`
> the plan file in the same commit as its fix. Load `commit-messages`; commit with one `-m` sentence; then check:
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
> amending on the spot if either prints; `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
> then the final test result.

## Verification commands

- F: `./gradlew :data:formats:desktopTest :libs:chordsheet:desktopTest` and
  `./gradlew :data:source:local:implementation:desktopTest --tests '*ImportCorpusTest*'` (before and after; read
  `report.txt`; no document's overall score may fall); `:app:web:compileKotlinWasmJs`.
- P, K: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin`, the plan's UI test with
  `./gradlew :tools:screenshots:test --tests '<the test class>'`, then `:app:android:compileDebugKotlin`,
  `:app:web:compileKotlinWasmJs`, and `:app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Merging

In the order F, P, K, as lanes report: list each lane's touched paths
(`git -C <worktree> log --stat --format=%s $START..HEAD`) and stop on any outside its files; then in the main checkout
`git cherry-pick $START..<lane HEAD>`. Run `./gradlew desktopTest` after each lane; after the last,
`./gradlew :tools:screenshots:test` and the full build
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS`
then `./gradlew :app:web:wasmJsBrowserDistribution` on its own (it OOMs combined). A break is fixed by one more
commit. `git worktree remove` each lane.

## Finish

`git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0, one
commit per plan, `git status` clean, `git worktree list` shows only the checkout. If no plan file is left, delete
`documentation/issues/` and commit `Remove the review plans.`. Do not push. Update the memory note `55th-review.md`.
