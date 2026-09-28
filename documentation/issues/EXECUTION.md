<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the ninth review

You are the orchestrator. Follow the `codebase-review` skill's section 4. This file fills it in for this sweep.

## Preconditions

Stop and tell the user if any of these fails:

- `git status` is clean apart from `documentation/issues/`.
- The README's Decisions section records D1 (plan 03 rejected and deleted) and D2 (plan 06, option A).
- The challenge has run, and the README says so.
- The unit tests pass:

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest
```

Then:

1. Commit the plans if they are untracked (`Add the ninth review plans.`).
2. Note `START=$(git rev-parse HEAD)`.

## Lanes

Two lanes, so both may build at once (the cap is two).

| Lane | Worktree | Plans, in order |
|------|----------|-----------------|
| A | `../Campfire-lane-a` | 01, 02 |
| B | `../Campfire-lane-b` | 07, 04, 05, 06 |

Create each lane's worktree with `git worktree add --detach ../Campfire-lane-<x> $START`. Neither lane needs
`local.properties`.

## Subagent prompt

Spawn one `general-purpose` agent per lane, in one message. Fill in `<LANE>`, `<PLANS>` and `<WORKTREE>` for each:

> You carry out lane <LANE> of the ninth Campfire review, in the detached git worktree `<WORKTREE>`. Work only inside
> it: never touch `/Users/pandulapeter/Projects/Campfire` itself. Never branch, merge, rebase, push, bump the version or
> dispatch a workflow.
>
> Carry out the plans <PLANS> from `documentation/issues/`, one at a time, in that order.
>
> Load the `code-style` skill before the first edit, and the `commit-messages` skill before the first commit.
>
> For each plan:
>
> 1. Read the plan and every file it names. Where lines have moved, find the code by the quoted snippet.
> 2. Implement exactly the plan, taking its recommended option, or the option the README's Decisions section records
>    as chosen.
>    - If the plan is wrong or impossible, or its "drop this plan if" condition holds, do not improvise another fix.
>    - Instead, `git checkout -- .`, leave the plan file, note why, and go on to the next plan.
> 3. Add the tests the plan asks for. Update the `CLAUDE.md` files and the strings it names, in both `values/` and
>    `values-hu/`.
> 4. Verify with the unit tests of the modules you changed (`./gradlew :<module>:desktopTest`), and with a compile of
>    every platform the change touches:
>    - desktop: `:<module>:compileKotlinDesktop`, or `:app:desktop:compileKotlin` for `:presentation`;
>    - `androidMain`: `:app:android:compileDebugKotlin`;
>    - `iosMain`: `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    - `wasmJsMain`: `:app:web:compileKotlinWasmJs`.
>
>    Plan 05 touches all four targets.
>    - `DesktopSyncAuthenticatorTest` holds port 53682, so rerun it alone before calling the lane red.
>    - Retry a corrupt incremental cache with `-Pkotlin.incremental=false`.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Commit with a single `-m` sentence in the repo's voice, one fix per commit, with no trailers. Then run these checks
>    and amend on the spot if either prints anything:
>
>    ```
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"
>    ```
>
> Skip manual checks that need a device, an account or a network condition, and report them.
>
> Report in this form:
> - one line per landed plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what>`;
> - then `Not done: NN — <why>` for any plan not carried out;
> - then the final test result.

## Merge

Merge in the order A, then B. For each lane:

1. In the main checkout, run `git cherry-pick $START..<lane HEAD>`.
2. Run the unit tests above.
3. Run `git worktree remove ../Campfire-lane-<x>`.

The lanes share no file. If a conflict still appears, merge it by keeping both sides' intent.

After lane B, run the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS
```

Fix a break with one more commit. Never rewrite a landed commit.

## Finish

1. Plan files still present are the skipped ones. Trim the README to them, or ask the user whether to delete the folder.
2. Run the final checks:
   - `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0.
   - `git log --oneline $START..HEAD` shows one line per landed plan.
   - `git status` is clean.
   - `git worktree list` shows only the checkout.
3. Report the commit count, the skipped plans and why, and the manual checks owed.
4. Update the `ninth-review` memory.

**Do not push.**
