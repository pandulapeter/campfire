<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 37th review plans

You orchestrate; lane subagents implement. Repository `/Users/pandulapeter/Projects/Campfire`, branch `master`.
Follow `.claude/skills/codebase-review/SKILL.md` section 4; this file fills it in for this sweep.

## Preconditions (stop and tell the user if one fails)

- `git status` is clean apart from `documentation/issues/`.
- Every plan carries a `**Challenged:**` line (all five do).
- `README.md`'s Decisions are answered (03's paste half).
- `./gradlew desktopTest` passes.

Commit the plans if untracked (`Add the 37th review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans, in order |
|------|----------|-----------------|
| T | `../Campfire-lane-t` | 01, 02 |
| I | `../Campfire-lane-i` | 03, 04, 05 |

`git worktree add --detach ../Campfire-lane-<x> $START` for each, then spawn both `general-purpose` subagents in one
message on the most capable model (two concurrent Gradle builds is the limit). `local.properties` is not needed.

## Lane prompt (fill in `<worktree>` and `<plans>`)

> You execute review plans in the detached worktree `<worktree>` of Campfire, plans `<plans>` from
> `<worktree>/documentation/issues/`, in that order. Every command starts with `cd <worktree> &&` or uses
> `git -C <worktree>`; scratch files go to
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/` only, and you run no
> script you did not write. Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> Per plan: read it and the files it names; load the `code-style` skill before the first edit. Implement exactly the
> plan, taking the recommended option; re-locate code by the quoted snippets. If the plan is wrong, impossible, or its
> "drop this plan if" holds, revert the working tree, leave the plan file, note why, and move on. Add the tests it
> asks for and update the CLAUDE.md files it names. Verify (below). `git rm` the plan file in the same commit. Load
> `commit-messages` and commit with a single `-m` sentence, one plan per commit. Then run
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
> amending on the spot if either prints, and `git show --stat HEAD` to confirm only the plan's files changed.
>
> Report: `NN: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final test result.

## Verification

- Lane T: `./gradlew :data:source:remote:implementation:desktopTest :data:source:remote:implementation:compileKotlinDesktop spotlessCheck`
  and `./gradlew :app:web:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64` (the atomic and engine
  code is commonMain). `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling the lane red.
- Lane I: `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :presentation:desktopTest :domain:implementation:desktopTest spotlessCheck`
  (the converter's goldens, `DocumentGoldenTest` included, run in `:chordpro` and `:domain:implementation`) and
  `./gradlew :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`.
- A corrupt incremental cache is retried with `-Pkotlin.incremental=false`.

## Merging

In the order T, then I. For each lane: `git -C <worktree> log --stat --format=%s $START..HEAD` and stop on any path
outside the lane's files (README's Lanes table); then in the main checkout `git cherry-pick $START..<lane HEAD>`.
Run `./gradlew desktopTest` after each lane; after the last, the full build
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
(run the web distribution alone if the combined build runs out of memory). A break is fixed by one more commit, never
by rewriting landed ones. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline $START..HEAD` is one line per plan; `git status` clean; `git worktree list` shows only the checkout.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`. Skipped
  plans stay with the README trimmed to them. Update the `37th-review` memory. Do not push.
