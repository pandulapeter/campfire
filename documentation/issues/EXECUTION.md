<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# Executing the 54th review's plans

The orchestrator follows `.claude/skills/codebase-review/SKILL.md` section 4 (and section 5: these are refactors, so
behaviour is preserved, moves carry their tests and docs, and moves are checked mechanically). What follows is this
sweep's concrete form of it. Subagents run on Opus.

## Preconditions

Stop and tell the owner if any fails: the tree is clean apart from this folder; the unit tests pass
(`./gradlew desktopTest`). Commit this folder (`Add the 54th review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Base | Worktree |
|---|---|---|---|
| H | 02, 01, 03, 04 | `START` | `../Campfire-lane-h` |
| D | 10, 08 | `START` | `../Campfire-lane-d` |
| I | 05, 06, then 07 only if the corpus gate holds | `HEAD` after H merged | `../Campfire-lane-i` |
| P | 11, 12, 13, 14, 15, 16, 20 | `HEAD` after H merged | `../Campfire-lane-p` |

Start H and D together (`git worktree add --detach ../Campfire-lane-<x> START`). Merge H as soon as it reports, then D,
then cut I and P from the new `HEAD` and run them together. Never more than two Gradle builds at once: H and D, then I
and P. Since the pre-commit hook runs `desktopTest`, every lane commit builds; that is expected.

**The corpus gate for 07:** (the corpus exists since 2026-11-02, 61 documents) run 07 only if the owner has set `campfire.importCorpus` in the main checkout's
`local.properties` to a folder holding real documents with their `.cho` files; pass it to the lane as
`-Pcampfire.importCorpus=<path>` (a worktree has no `local.properties`). Otherwise lane I ends after 06 and 07 stays.

## The lane prompt

> You carry out lane `<X>` of the 54th Campfire review, in the worktree `<WORKTREE>` (detached; never create a branch).
> Plans, in this order: `<PLANS>`, in `<WORKTREE>/documentation/issues/`. Read each plan whole and the files it names
> before touching it; load the `code-style` skill before the first edit and `commit-messages` before the first commit.
>
> - Implement exactly the plan, its recommended option. A plan split into steps is one commit per step, each leaving
>   every platform compiling and `desktopTest` green; the plan file is `git rm`ed in the commit of its last step (or
>   its only commit). Re-locate code by the quoted snippets: earlier plans moved lines.
> - If a plan is wrong or impossible, or its own drop or stop condition holds, do not improvise: revert the working
>   tree, leave the plan file, note why, go on. Plan 13 step 4: one holder per commit, and a holder whose start needs
>   an unlisted reorder stays in the view model (say which). Plan 20 only measures and reports; it never splits.
> - Behaviour is preserved. A bug found on the way is its own commit, never folded into a move. No test is `@Ignore`d;
>   a red test exposing a real defect is fixed in a commit before the test's, or the plan stops and you report it.
> - `check_sizes.py` (edit hook, pre-commit, CI) fails a file or top-level function already past its size limit that
>   grows, and a class constructor that passes 12 parameters or, already past, gains one: a lane moving code out of
>   `CampfireViewModel`, `ImportController`, or `ChordSheetConverter` must shrink, never grow, them in every commit.
> - Verify each commit: `./gradlew desktopTest` (the hook runs it too) and the compile of every platform it touches —
>   desktop `:app:desktop:compileKotlin`, `androidMain` `:app:android:compileDebugKotlin`, `iosMain`
>   `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `wasmJsMain` `:app:web:compileKotlinWasmJs`; UI test changes
>   (01, 12, 13, 14) also `./gradlew :tools:screenshots:test`. `DesktopSyncAuthenticatorTest` failing on port 53682
>   before plan 02 lands is the known collision: rerun it alone. A corrupt incremental cache: `-Pkotlin.incremental=false`.
> - Isolation: every command starts with `cd <WORKTREE> &&`; scratch files only in
>   `<scratchpad>/lane-<x>/`; never run a script you did not write; never `git stash`; after each commit,
>   `git show --stat HEAD` must name only that plan's files.
> - Each commit message is one imperative sentence ending in a period, nothing else. After each commit run:
>   `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>   `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo ATTRIBUTION`;
>   amend on the spot if either prints.
> - Never branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> Report: `NN[/step]: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final
> `desktopTest` result, and for plan 20 its numbers.

## Merging

For each lane, in the order H, D, then I and P as they finish: list what its commits touched
(`git -C <worktree> log --stat --format=%s <base>..HEAD`) and stop on a path outside the lane's files (README's lane
table); then `git cherry-pick <base>..<lane HEAD>` in the main checkout. Shared files (README's list) are merged
keeping every sentence and key from both sides. Run `./gradlew desktopTest` after each lane; after the last,
`./gradlew :tools:screenshots:test` and the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

`git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0,
`git status` is clean, and `git worktree list` shows only the checkout. Report the commits per lane, the plans left
(07 waiting for the corpus is expected) and why, and plan 20's numbers. Update the `54th-review` memory. When only
`README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`; otherwise trim the
README to the plans left. Do not push.
