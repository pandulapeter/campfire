# Executing the low-end performance plans

You are the orchestrator. The user starts you with "Follow `documentation/issues/EXECUTION.md`." The plans were
written at `491c4254a`. The `codebase-review` skill, section 4, is the authority, and this file is that section made
concrete for this sweep.

## Preconditions

Stop and tell the user if any of these fails.

1. `git status` is clean apart from `documentation/issues/`.
2. Every decision in `README.md` → Decisions is answered.
   They are all answered (2026-10-07):
   - D-03: plan 03 is carried out, shipping ANGLE now;
   - D-16: plan 16 is carried out;
   - D-20: plan 20 is carried out with option (c);
   - D-40: declined, so plan 40 was removed and lane M is plan 41 alone.
3. The unit tests pass:
   `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`
   and `node --test app/web/tests/*.cjs`.
4. If the plans are untracked, commit them as `Add the low-end performance review plans.` (load `commit-messages`
   first). Then `START=$(git rev-parse HEAD)`.

## Lanes

Each lane gets one detached worktree next to the checkout: `git worktree add --detach ../Campfire-lane-<x> $START`.
No branches. **Run at most two Gradle-building lanes at once.** Start D and S first, then W (Node only, it can run
alongside), M as soon as a slot frees, and U last.

| Lane | Worktree | Plans, in this order |
|---|---|---|
| D | `../Campfire-lane-d` | 01, 02, 03 (03's ANGLE DLL step stays on `createReleaseDistributable`, so it runs before 01's training run) |
| W | `../Campfire-lane-w` | 20 |
| S | `../Campfire-lane-s` | 10, 11, 12, 13, 14, 15, 16 |
| M | `../Campfire-lane-m` | 41 |
| U | `../Campfire-lane-u` | 30, 31, 32, 33, 34, 35 (35 strictly last) |

Lane D's manual checks need no Dropbox key, so no lane needs `local.properties` copied in.

## The lane prompt

Spawn one `general-purpose` agent per lane with this prompt, filling in `<WORKTREE>`, `<LANE>` and `<PLANS>`:

> You carry out lane <LANE> of the Campfire low-end performance review, in the detached git worktree <WORKTREE> and
> nowhere else. Never touch `/Users/pandulapeter/Projects/Campfire` itself. Plans, in this order: <PLANS>. They are
> in `<WORKTREE>/documentation/issues/`, and the decisions are in that folder's `README.md`. Apply the user's
> answers: <DECISIONS FOR THIS LANE>.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking the recommended option (or the decided one). Find code that has moved by the
>    quoted snippet. If the plan is wrong, impossible, or its own "Drop this plan if" condition holds, do not
>    improvise another fix: revert the working tree, leave the plan file in place, note why, and move on.
> 3. Add the tests the plan asks for, and update the `CLAUDE.md` files and the strings it names (both `values/` and
>    `values-hu/`).
> 4. Verify with the tests and a compile of every platform the change touches:
>    - unit tests: `./gradlew <the desktopTest tasks of the modules you changed>` (the full list is in the
>      preconditions of EXECUTION.md);
>    - desktop: `:<module>:compileKotlinDesktop`, or `:app:desktop:compileKotlin` for `:presentation` and the apps;
>      for lane D also `:app:desktop:createReleaseDistributable`, and for plan 01 its new training task on this Mac
>      (`-Xlog:cds` shows the top layer mapping);
>    - `androidMain`: `:app:android:compileDebugKotlin`;
>    - `iosMain`: `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    - `wasmJsMain`: `:app:web:compileKotlinWasmJs`;
>    - web loader: `node --test app/web/tests/*.cjs`.
>
>    Run one Gradle build at a time. `DesktopSyncAuthenticatorTest` holds port 53682, so rerun it alone before calling
>    a run red. Retry a corrupt incremental cache with `-Pkotlin.incremental=false`. Skip manual checks that need a
>    device, an account, a store or a network condition, and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill. Commit with a single `-m` sentence: one fix, one commit. Then run:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`. If either
>    prints, amend on the spot.
>
> Never branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Report one line per plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`. Then
> `Not done: NN — <why>`, then the final test result.

## Merging

Merge in this order: **D, W, S, M, U**. Wait for a lane to report before merging it.

1. In the main checkout, run `git cherry-pick $START..<lane HEAD>`.
2. Resolve conflicts keeping both sides' intent. `CLAUDE.md` paragraphs and `strings.xml` are merged as a
   word-level three-way merge, keeping every sentence and key from both sides. These are expected to conflict only
   textually:
   - `CampfireViewModel.kt`, where S's plans 14 and 16 and U's plan 32 each touch their own region;
   - `presentation/CLAUDE.md`;
   - the root `CLAUDE.md`.
3. Run the unit tests after each lane.
4. After the last lane, run the full build:
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
   A break is fixed by one more commit, never by rewriting landed commits.
5. Remove each worktree once it is merged: `git worktree remove ../Campfire-lane-<x>`.

## Finish

1. Plan files still present are the skipped ones. If any remain, trim `README.md` down to them and ask the user
   whether to keep the folder.
2. Run the final checks:
   - `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
   - `git log --oneline $START..HEAD` shows one line per plan, plus any build-fix commits;
   - `git status` is clean;
   - `git worktree list` shows only the checkout.
3. Report the commit count, the skipped plans with the reasons, and the manual checks owed. Copy those out of the
   README first.
4. Update the memory note `twentieth-review.md` with the commit range, what was skipped and the manual checks still
   owed.
5. If no plan file is left, delete the folder and commit `Remove the review plans.`.
6. **Do not push.**
