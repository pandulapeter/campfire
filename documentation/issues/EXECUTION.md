<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 43rd review plans

You are the orchestrating agent. The user started you with "Follow documentation/issues/EXECUTION.md." Read
`README.md` here first (lanes, merge order, shared files, decisions). Load the `codebase-review` skill, section 4.
Spawn lane agents with `model: opus`. Do not push anything.

## Preconditions

Stop and tell the user if any fails:

1. `git status` is clean apart from `documentation/issues/`.
2. The challenge ran on every plan (each plan's **Challenged:** line, or 06's "sound" in the README) and every decision
   is answered (the README's Decisions: all are).
3. `./gradlew desktopTest` passes. A failing `DesktopSyncAuthenticatorTest` (port 53682) is a known false alarm: rerun
   it alone.

Then commit the plans: `git add documentation/issues && git commit -m "Add the 43rd review plans."` (load
`commit-messages` first), and note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane, next to the checkout: `git worktree add --detach ../Campfire-lane-<x> <base>`.

| Lane | Plans, in order | Base |
| --- | --- | --- |
| j | 01, 09, 08 | `START` |
| k | 05, 06 | `START` |
| c | 07 | `START` |
| s | 04, 03 | `START` |
| i | 02 | `START` |

At most two Gradle builds at once: start J and K first (spawned in one message); start C, S, and I, in that order,
as earlier lanes finish. Every lane is cut from `START`: no plan relies on another lane's code (09 relies on 01's
`JsonBounds`, in the same lane).

## The subagent prompt

Fill `<x>`, `<plans>`, and `<base>`; the worktree path is given at spawn time.

```
You carry out lane <X> of the 43rd review plans for Campfire, in the detached git worktree <absolute worktree path>
(base <base>). Lane plans, in this order: <plans, e.g. 01, 02, 03, 04>, each a file in documentation/issues/ of your
worktree. Read documentation/issues/README.md there first (shared files, what the lanes own).

Keeping lanes apart: scratch files go only to <scratchpad>/lane-<x>/ and you run no script you did not write; every
command starts with `cd <absolute worktree> &&` (or uses `git -C <absolute worktree>`); after every commit run
`git show --stat HEAD` and confirm it touched only that plan's files (and, for shared files, only your plan's own
members, keys, and paragraphs); never `git stash`; never branch, merge, rebase, push, bump the version, or dispatch a
workflow.

Per plan, in order:
1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
2. Implement exactly the plan (its **Decided** lines are final). Re-locate moved code by the quoted snippet. If the
   plan is wrong or impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert
   the working tree, leave the plan file, note why, and move on.
3. Add the tests it asks for and update the CLAUDE.md files and strings it names (English and Hungarian; Oxford comma
   in English prose). No test is ever @Ignore'd: if a new test fails on a real defect, fix that defect in its own
   commit before the test's. Growing a file already past its size limit is fine; pushing one past it is not.
4. Verify with the commands below: the tests, and a compile of every platform the change touches.
5. `git rm` the plan file in the same commit as its fix.
6. Load the `commit-messages` skill, then commit with a single -m sentence: one plan, one commit. Then run both
   checks and amend on the spot if either fires:
   test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
   git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"

Report, one line per plan: `NN: <hash> "<message>" - verified: <commands>; skipped manual: <what, why>`; then
`Not done: NN - <why>`; then the final test result. Name a skipped manual check only where a mistake would hurt.
```

## Verification commands

- Unit tests of a module: `./gradlew :<module>:desktopTest` (for example `:data:sync:implementation:desktopTest`,
  `:domain:implementation:desktopTest`, `:presentation:desktopTest`).
- commonMain or desktopMain: `:<module>:compileKotlinDesktop` and `:app:desktop:compileKotlin`.
- androidMain: `:app:android:compileDebugKotlin`.
- iosMain: `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
- wasmJsMain: `:app:web:compileKotlinWasmJs`.
- `:chordpro` (lane K): `./gradlew :chordpro:desktopTest`, and `:app:web:compileKotlinWasmJs` (it is shared by all).
- No plan adds a UI test; run `./gradlew :tools:screenshots:test` once after the last merge.
- Known false alarms: `DesktopSyncAuthenticatorTest` (port 53682): rerun alone. On a corrupt incremental cache, rerun
  with `-Pkotlin.incremental=false`.
- At most two concurrent Gradle builds across all lanes.

## Merging

In the README's order (J, C, K, S, I), as lanes report:

1. List what each lane commit touched: `git -C ../Campfire-lane-<x> log --stat --format=%s <base>..HEAD`. Stop on any
   path outside the lane's files (the plans' **Files:** lines), except the named shared files.
2. In the main checkout: `git cherry-pick <base>..<lane HEAD>`. Resolve conflicts keeping both sides' intent:
   `CLAUDE.md` paragraphs, `strings.xml`, and `CampfireViewModel.kt` (02 and 03 each add a constructor parameter) as a
   word-level three-way merge with every sentence and key from both sides, never one side's version whole.
3. Run the tests after each lane (`./gradlew desktopTest`; `:tools:screenshots:test` after the last). Then
   `git worktree remove ../Campfire-lane-<x>`.
4. After the last lane, the full build:
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
   A break is fixed by one more commit (for example `Fix the iOS build after the download cap.`), never by
   rewriting landed commits.

## Finish

- Final checks: `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'`
  prints 0; `git log --oneline START..HEAD` is one line per plan; `git status` is clean; `git worktree list` shows only
  the checkout.
- Update the memory file `43rd-review.md` in
  `/Users/pandulapeter/.claude-personal/projects/-Users-pandulapeter-Projects-Campfire/memory/` (and its line in
  `MEMORY.md`): the commit range of the landed plans, and what was skipped and why.
- Report the commit count and the skipped plans with reasons; name a hand check only where a mistake would hurt.
- When only `README.md` and `EXECUTION.md` are left in `documentation/issues/`, delete the folder and commit it as
  `Remove the review plans.` without asking. If skipped plans remain, leave the folder, trimmed to them, until the user
  decides. Do not push.
