<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 38th review's plans

The orchestrator's brief. `README.md` holds the index, the lanes, the shared-file rules, and the decisions.

## Preconditions (stop and tell the user if one fails)

- `git status` is clean apart from `documentation/issues/` and the user's own work under `tools/` (which no lane
  touches; if it is still there, leave it and say so).
- Every plan file carries a `**Challenged:**` line, and every decision in the README has an answer recorded under it.
- `./gradlew :chordpro:desktopTest :presentation:desktopTest` passes.
- Commit the plans first: `git add documentation/issues && git commit -m "Add the 38th review plans."`; then
  `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans in order | Base |
|---|---|---|---|
| S | `../Campfire-lane-s` | 01, 02, 03, 04, 05, 07 | `START` |
| U | `../Campfire-lane-u` | 08 | `START` |
| T | `../Campfire-lane-t` | 09, 10, 11, 12, 13, 14, 15, 16 | `START` |

At most two Gradle builds at once: start S and U together, T when U reports (U is one plan). Create each worktree
detached: `git worktree add --detach ../Campfire-lane-<x> $START`.

## The subagent prompt (one per lane; fill in the worktree path)

> You carry out the plans of lane <X> of the Campfire review, in order, in the worktree `<absolute worktree path>` only.
> Every command starts with `cd <absolute worktree path> &&` or uses `git -C <absolute worktree path>`; scratch files go
> to `<scratchpad>/lane-<x>/` only, and you run no script you did not write; never `git stash`, branch, merge, rebase,
> push, bump the version, or dispatch a workflow. Per plan: (1) read `documentation/issues/<NN>-*.md` and every file it
> names, and load the `code-style` skill before the first edit; (2) implement exactly the plan, taking the recommended
> option or the decision the README records, re-locating moved code by the quoted snippet; if the plan is wrong,
> impossible, or its "drop this plan if" holds, do not improvise: revert the tree, leave the plan file, note why, move
> on; (3) add the tests it asks for and update the `CLAUDE.md` files and strings (both locales) it names; growing a file
> already past its size limit is fine, pushing one past it is refused (`check_sizes.py` runs on every edit); (4) verify:
> `./gradlew :chordpro:desktopTest :presentation:desktopTest :chordpro:spotlessCheck :presentation:spotlessCheck` and
> `./gradlew :app:desktop:compileKotlin`; a plan touching `tools/screenshots` also runs its test with
> `./gradlew :tools:screenshots:test --tests '<the test class>'`; (5) `git rm` the plan file in the same commit as its
> fix; (6) load the `commit-messages` skill and commit with one `-m` sentence, one plan one commit, then check
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo ATTRIBUTION`, and
> `git show --stat HEAD` confirms the commit touched only that plan's files. Report at the end: per plan
> `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`, then the
> final test result.

## Merging, in the order S, U, T

For each lane as it reports: list the paths its commits touched (`git -C <worktree> log --stat --format=%s
$START..HEAD`) and stop on any outside the lane's files in the README; then in the main checkout
`git cherry-pick $START..<lane HEAD>`. Conflicts in `strings.xml` and the `CLAUDE.md` files are word-level three-way
merges keeping every key and sentence of both sides. After each lane: `./gradlew :chordpro:desktopTest
:presentation:desktopTest`; after T: `./gradlew :tools:screenshots:test` (20-minute timeout) and the full build
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS`,
then `./gradlew :app:web:wasmJsBrowserDistribution` on its own (the combined build has run out of memory in the web
link). A break is fixed by one more commit, never by rewriting landed ones. Then `git worktree remove
../Campfire-lane-<x>`.

## Finish

- Plan files still present are the skipped ones: leave them with the README trimmed to them, or ask the user whether to
  delete the folder.
- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan plus any fix-up; `git status` is clean (but for `tools/`);
  `git worktree list` shows only the checkout.
- When no plan file is left: `git rm -r documentation/issues && git commit -m "Remove the review plans."`.
- Update the memory note `38th-review.md` with the commit range and what was skipped. **Do not push.**
