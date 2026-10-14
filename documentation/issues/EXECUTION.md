<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the thirtieth review's plans

You are the orchestrator. Load `.claude/skills/codebase-review/SKILL.md` and follow its section 4, made concrete here.
Read `README.md` in this folder first (lanes, merge order, shared-file rules, decisions).

## Preconditions

Stop and tell the user if any fails: the tree is clean apart from this folder and other sessions' work in
`tools/screenshots`; every plan carries a `**Challenged:**` line; the README's default decisions were not overridden
(if they were, amend the plans first); the unit tests pass. `git config core.hooksPath` prints `.githooks`. Commit the
plans: `Add the thirtieth review plans.` Then `START=$(git rev-parse HEAD)`.

## Wave 1 — lanes D, P, V

Worktrees: `git worktree add --detach ../Campfire-lane-<x> $START` for `d`, `p`, `v`. Each worktree runs
`git -C <worktree> config core.hooksPath .githooks` (worktrees share the config, so once is enough; check it). Spawn P
and V in one message; spawn D when either reports (at most two Gradle builds at once).

Lane plans, in order: **D**: 10. **P**: 06, 07. **V**: 01, 02, 03, 04, 05, 08, 09.

Subagent prompt (fill `<x>`, `<plans>`, `<worktree>`):

> You carry out plans `<plans>` of `documentation/issues/` in the worktree `<worktree>`, in that order. Follow
> `.claude/skills/codebase-review/SKILL.md` section 4 "Per plan" exactly: load `code-style` before the first edit and
> `commit-messages` before the first commit; implement exactly the plan (recommended options); one commit per step the
> plan names, `git rm` the plan file in its last commit; never improvise another fix — if a plan is wrong, revert and
> report it. Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`; scratch files only in
> `<scratchpad>/lane-<x>/`; never `git stash`, branch, merge, rebase, push. After every commit, `git show --stat HEAD`
> confirms only that plan's files. The pre-commit hook runs the size and license checks; when it asks for
> `check_sizes.py --update`, run it and include `.github/scripts/size_allowances.json` in the commit. Verify each plan
> with the root CLAUDE.md test command for the touched modules plus `:app:desktop:compileKotlin`,
> `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
> Report per the skill: `NN: <hashes> "<messages>" — verified: <commands>`, `Not done: NN — <why>`, final test result.

Merge in the order **D, P, V** as the skill says (path check, `git cherry-pick <base>..<lane HEAD>`, both sides kept in
CLAUDE.md and `strings.xml`, `size_allowances.json` conflicts resolved by rerunning `--update`), running the unit tests
after each lane. Remove the three worktrees.

## Wave 2 — lane R

Cut `../Campfire-lane-r` from the merged `HEAD`. One subagent, plans 11, 12, 13, 14, 15, 17, 16 in that order, same
prompt. These are mechanical moves: each plan's commits must compile on all four platforms one by one (run the four
compile tasks after every commit, not only per plan), and moves are checked with `git diff --color-moved=dimmed-zebra`.
After each plan, grep the repository for the old package names and paths (code, CLAUDE.md files, skills, `.github`,
`documentation/plans/`). Merge with `git cherry-pick`, then run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.

## Finish

Per the skill: checks on `START..HEAD` (no attribution, one line per commit), clean tree, only the main worktree.
`python3 .github/scripts/check_sizes.py` exits 0. Then offer a regression review of the landed range (angle: "did
these moves preserve behaviour", against `START`). Delete this folder and commit `Remove the review plans.` when no
plan file is left. Update the `review-history.md` memory with the landed range and anything skipped. **Do not push.**
