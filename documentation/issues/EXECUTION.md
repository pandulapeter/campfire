<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 32nd review plans

Started with *"Follow `documentation/issues/EXECUTION.md`."* The process is the `codebase-review` skill's section 4; this
is it for this sweep.

## Preconditions (stop and tell the user if any fails)

- `git status` is clean apart from `documentation/issues/`; the README's Decisions are answered; the challenge has run
  (each plan's header says so or the README records it); the unit tests pass (root `CLAUDE.md`, Tests).
- Commit the plans if untracked: `Add the 32nd review plans.` Then `START=$(git rev-parse HEAD)`.

## Lanes

Detached worktrees next to the checkout, at most two Gradle builds at once: start S and E together, P when either ends.

| Lane | Worktree | Plans, in order |
|------|----------|-----------------|
| S | `../Campfire-lane-s` | 01, 02 |
| E | `../Campfire-lane-e` | 06 |
| P | `../Campfire-lane-p` | 03, 04, 05 |

`git worktree add --detach ../Campfire-lane-<x> $START`; no `local.properties` needed.

## Subagent prompt (fill in `<WORKTREE>`, `<X>`, `<PLANS>`)

> You carry out review plans `<PLANS>` of the Campfire repo, in that order, in the detached worktree `<WORKTREE>` and
> nowhere else. Every shell command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files only in
> `<scratchpad>/lane-<X>/`; never run a script you did not write; never `git stash`, branch, merge, rebase, push, bump
> the version, or dispatch a workflow.
>
> Per plan: read `documentation/issues/NN-*.md` and every file it names, and the nearest `CLAUDE.md` files. Load the
> `code-style` skill before the first edit. Implement exactly the plan, taking its recommended option (and the user's
> answer recorded in `documentation/issues/README.md` → Decisions). Re-locate code by the quoted snippets. If the plan is
> wrong or impossible, or its own "drop this plan if" holds, revert the working tree, leave the plan file, note why, and
> move on — never improvise another fix. Add its tests, update the `CLAUDE.md` lines it names. A plan that grows a file
> past its `.github/scripts/size_allowances.json` entry raises that entry by the net lines it adds, in the same commit,
> and says so in the report (standing user ruling; do not stop to ask).
>
> Verify: the desktop tests of every module touched (`./gradlew :<module>:desktopTest`, the list is in the root
> `CLAUDE.md`), `spotlessCheck`, and a compile of every platform the change touches — desktop
> `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`), `iosMain` →
> `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `wasmJsMain` or common code → `:app:web:compileKotlinWasmJs`, `androidMain`
> or common code → `:app:android:compileDebugKotlin`. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone
> before calling the lane red; a corrupt incremental cache is retried with `-Pkotlin.incremental=false`.
>
> `git rm` the plan file in the same commit as its fix. Load `commit-messages`, commit with a single `-m` sentence (one
> plan, one commit), then run
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
> amending on the spot if either prints, and `git show --stat HEAD` to confirm only that plan's files changed.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; allowance raised: <file +n or none>`, then `Not done: NN — <why>`,
> then the final test result.

Lane-specific notes for the prompt:

- **S**: modules `:data:sync:implementation` (tests), compile desktop + `:app:ios:linkDebugFrameworkIosSimulatorArm64` +
  `:app:web:compileKotlinWasmJs` (common code).
- **E**: `:tuner:implementation:desktopTest`, and `:app:ios:linkDebugFrameworkIosSimulatorArm64` (the change is `iosMain`).
- **P**: `:presentation:desktopTest`, `:chordpro:desktopTest`, `:tools:screenshots:test`, and all four platform compiles
  (05 adds `:chordpro` public API).

## Merging (main checkout, in order S, E, P)

For each lane: `git -C ../Campfire-lane-<x> log --stat --format=%s $START..HEAD` and stop on any path outside the lane's
files (README → Lanes); then `git cherry-pick $START..<lane HEAD>`. Conflicts keep both sides' intent (`CLAUDE.md` and
`size_allowances.json` merged entry by entry). Run the unit tests after each lane, and after the last the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
(run the web distribution alone if the combined build runs out of memory). A break is fixed by one more commit. Then
`git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan (plus any build fix); `git status` clean; `git worktree list`
  shows only the checkout.
- Report the commit count and skipped plans with reasons. Update the 32nd entry of the review-history memory.
- If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` Do not push.
