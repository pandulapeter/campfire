<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 36th review's plans

You are the orchestrator. Load the `codebase-review` skill and follow its section 4; this file fills it in for this
sweep. Never push, branch, merge, rebase, bump the version, or dispatch a workflow.

## Preconditions (stop and tell the user if one fails)

1. `git status` is clean apart from `documentation/issues/`.
2. Every plan carries a `**Challenged:**` line (all 18 do).
3. Every decision in `README.md` is answered (all were, on 2026-10-19: 04 and 13 take their recommended options).
4. `./gradlew desktopTest` passes.

Then commit the plans if untracked (`Add the 36th review plans.`) and note `START=$(git rev-parse HEAD)`.

## Lanes and waves

| Wave | Lane | Plans, in order | Base |
|---|---|---|---|
| 1 | T | 01, 02, 03, 04, 05, 06, 07, 08 | `START` |
| 1 | S | 13, 14, 15 | `START` |
| 2 | C | 09, 10, 11, 12 | `START` (start when a wave 1 lane finishes, so at most two Gradle lanes run at once) |
| 2 | A | 18 | `START` (after C or a wave 1 lane frees a slot) |
| 3 | U | 16, 17 | main checkout `HEAD` after T and S merged |

Worktrees: `git worktree add --detach ../Campfire-lane-<x> <base>`, one `general-purpose` subagent per lane. At most
two lanes build at once (24 GB Mac, Kotlin daemon OOM). `local.properties` is not needed by any lane.

## Subagent prompt (fill in `<x>`, `<WORKTREE>`, `<PLANS>`, `<BASE>`)

> You carry out lane `<x>` of Campfire's 36th review in the detached worktree `<WORKTREE>` (absolute path), based on
> `<BASE>`. Plans, in this order: `<PLANS>` (files in `<WORKTREE>/documentation/issues/`). Rules:
> - Every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go to
>   `<scratchpad>/lane-<x>/` only; never run a script you did not write. Never `git stash`, branch, merge, rebase, or
>   push.
> - Per plan: read the plan and the files it names; load the `code-style` skill before the first edit; implement
>   exactly the plan, taking its recommended option (re-locate code by the quoted snippets). If the plan is wrong,
>   impossible, or its "drop this plan if" holds, revert the working tree, leave the plan file, note why, and move on —
>   do not improvise another fix. Add the tests it asks for and update the `CLAUDE.md` files and strings it names.
> - Verify: the tests of every module the plan touches
>   (`./gradlew :<module>:desktopTest`, `:presentation:desktopTest`, `:tools:screenshots:test` when it changes
>   `tools/screenshots`), `spotlessCheck` on touched modules, and a compile of every platform the change touches:
>   desktop `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`), `androidMain` →
>   `:app:android:compileDebugKotlin`, `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `wasmJsMain` (and
>   any common change that the web reads differently, e.g. the browser-only bounds of 03 and 11) →
>   `:app:web:compileKotlinWasmJs`. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling the
>   lane red; a corrupt incremental cache is retried with `-Pkotlin.incremental=false`.
> - `git rm` the plan file in the same commit as its fix. Load the `commit-messages` skill; commit with one `-m`
>   sentence, one plan per commit. Then run
>   `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>   `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo ATTRIBUTION`,
>   amending on the spot if either prints, and `git show --stat HEAD` to confirm only that plan's files changed.
> - Report: `NN: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final test result.

## Merging

In order **T, S, C, A, U**, as lanes report. First list each lane commit's paths
(`git -C <worktree> log --stat --format=%s <base>..HEAD`) and stop on any outside the lane's files in `README.md`; then,
in the main checkout, `git cherry-pick <base>..<lane HEAD>`. Conflicts are expected only in
`data/source/remote/implementation/CLAUDE.md` (T, C, U) and the `:data:source:remote:api` KDoc (T, U): merge by
sentence, keeping every lane's wording. Run `./gradlew desktopTest` after each lane. After S merges, cut lane U from
`HEAD`. After the last lane, run the full build, with the web production link on its own if the combined run OOMs:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS
./gradlew :app:web:wasmJsBrowserDistribution
./gradlew :tools:screenshots:test
```

A break is fixed by one more commit, never by rewriting landed ones. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline START..HEAD` is one line per plan (plus fix-ups); `git status` is clean; `git worktree list`
  shows only the checkout.
- Report the commit count and any skipped plans with reasons. Update the memory note `36th-review.md` and the 36th line
  of `review-history.md`: the landed range and what was skipped.
- If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` without asking; else
  trim `README.md` to the skipped plans and ask the user.
