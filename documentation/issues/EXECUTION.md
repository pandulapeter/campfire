<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 40th review's plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It is the
`codebase-review` skill's section 4 for this sweep; load that skill and `commit-messages` first.

## Preconditions — stop and tell the user if any fails

1. `git status` is clean apart from `documentation/issues/`.
2. `README.md`'s Challenge section is there (it ran on all fourteen plans) and every item under **Decisions** is
   answered. A plan whose decision went against it is dropped or follows the answer the README records.
3. `./gradlew desktopTest` passes.
4. If the plans are untracked: `git add documentation/issues && git commit -m "Add the 40th review plans."`.
   Then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order    | Worktree                          | Cut from |
|------|--------------------|-----------------------------------|----------|
| S    | 01, 02, 13, 14     | `../Campfire-lane-s`              | `START`  |
| T    | 03, 04, 05, 06, 07 | `../Campfire-lane-t`              | `START`  |
| N    | 08, 09             | `../Campfire-lane-n`              | `START`  |
| U    | 10, 11, 12         | `../Campfire-lane-u`              | `START`  |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. At most two Gradle builds at once: start S and T
together, then N and U as those finish. Spawn each lane as a `general-purpose` subagent on the most capable model.

## Lane prompt (fill in `<x>`, `<WORKTREE>`, `<PLANS>`)

> You carry out lane <x> of the 40th Campfire review: plans <PLANS> in `documentation/issues/`, in that order, in the
> detached worktree `<WORKTREE>` (absolute path). Read `documentation/issues/README.md`'s Decisions first: where the
> user's answer changes a plan, follow the answer.
>
> Isolation: every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go to
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/` only; never run a
> script you did not write. Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
> After every commit, `git show --stat HEAD` must list only that plan's files (plus the plan file's deletion).
>
> Per plan: read it and every file it names; load `code-style` before the first edit. Implement exactly the plan,
> taking the recommended option; re-locate moved code by the quoted snippet. If the plan is wrong, impossible, or its
> own "drop this plan if" holds, do not improvise: revert the working tree, leave the plan file, note why, move on. Add
> the tests it asks for (never `@Ignore`; a test failing on a real defect fixes that defect first, in its own commit),
> update the `CLAUDE.md` files it names. Verify (below), reading the test result from the XML under
> `build/test-results`. `git rm` the plan file in the same commit as its fix. Load `commit-messages` and commit with a
> single `-m` sentence, then:
>
> ```
> test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
> git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
> ```
>
> amending on the spot if either prints. Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual:
> <what, why>`, then `Not done: NN — <why>`, then the final test result.

## Verification per lane

- S: `./gradlew :data:sync:implementation:desktopTest :data:source:remote:implementation:desktopTest
  :app:desktop:compileKotlin :app:web:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64`
- T: `./gradlew :tuner:implementation:desktopTest :presentation:desktopTest :app:desktop:compileKotlin
  :app:android:compileDebugKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64` (03 desktop, 04 Android, 06 iOS)
- N: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`, plus
  `:tools:screenshots:test` if a plan adds a UI test
- U: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :tools:screenshots:test --tests '*Instrument*'`
- Every lane: `./gradlew spotlessCheck`, `python3 .github/scripts/check_sizes.py`.
- Known false alarms: `DesktopSyncAuthenticatorTest` holds port 53682 (rerun it alone); a corrupt incremental cache
  is retried with `-Pkotlin.incremental=false`.

## Merging, in the order S, T, N, U

For each lane as it reports: `git -C <worktree> log --stat --format=%s $START..HEAD` and stop on any path outside the
lane's files (README's Lanes table). Then, in the main checkout, `git cherry-pick $START..<lane HEAD>`. Resolve
`CLAUDE.md` conflicts word by word, keeping every sentence from both sides. Run `./gradlew desktopTest` after each
lane; a break is fixed by one more commit, never by rewriting landed ones. Then `git worktree remove
../Campfire-lane-<x>`.

After the last lane: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64
:app:desktop:packageDistributionForCurrentOS`, then `./gradlew :app:web:wasmJsBrowserDistribution` on its own (the
combined build OOMs in the web link), then `./gradlew :tools:screenshots:test`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline $START..HEAD` is one line per plan (plus any build-fix commit).
- `git status` is clean, `git worktree list` shows only the checkout.
- Report the commit count and the skipped plans with reasons; name a hand check only where a mistake would hurt.
- Update the `40th-review` memory with the range and what was skipped.
- When no plan file is left, `git rm -r documentation/issues` and commit `Remove the review plans.` without asking;
  if skipped plans remain, trim the README to them and leave the folder. **Do not push.**
