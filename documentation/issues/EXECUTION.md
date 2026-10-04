# Executing the fourteenth review

Started with *"Follow `documentation/issues/EXECUTION.md`."* You orchestrate; lane subagents implement.

## Preconditions (stop and tell the user if any fails)

- `git status` is clean apart from `documentation/issues/`.
- Every plan carries a `**Challenged:**` line.
- Plan 10's decision is recorded in `README.md` (Decisions). If it is still awaiting the user, run lanes A, C, D and
  lane B without 10, and leave 10 in the folder.
- The unit tests pass:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest`

Then, if the plans are untracked, commit them: `Add the fourteenth review plans.` (load `commit-messages` first), and
note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane: `git worktree add --detach ../Campfire-lane-<x> $START`.

| Lane | Worktree               | Plans in order     |
|------|------------------------|--------------------|
| A    | `../Campfire-lane-a`   | 01, 02             |
| B    | `../Campfire-lane-b`   | 12, 11, 10         |
| C    | `../Campfire-lane-c`   | 20, 21, 22, 23, 24 |
| D    | `../Campfire-lane-d`   | 31, 32, 30         |

At most two concurrent Gradle builds: start A and B together, then D and C as they finish (C last, it is the largest).
`local.properties` is not needed by any lane.

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`)

> You work only in `<WORKTREE>`, a detached git worktree of the Campfire repo. Never touch
> `/Users/pandulapeter/Projects/Campfire` itself. Your lane is `<LANE>`; carry out these plans in this order:
> `<PLANS>`, each in `documentation/issues/`.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking its recommended option (plan 10: the option recorded in `README.md`'s
>    Decisions). Re-locate moved code by the quoted snippet. If the plan is wrong, impossible, or its own "drop this
>    plan if" condition holds, do not improvise: revert the working tree, leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files it names.
> 4. Verify: the module's `desktopTest`, plus a compile of every platform the change touches —
>    desktop `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation` and the apps);
>    `androidMain` → `:app:android:compileDebugKotlin`; `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    `wasmJsMain` → `:app:web:compileKotlinWasmJs`; web JS → `cd app/web && node --test tests/`. If
>    `DesktopSyncAuthenticatorTest` fails, rerun it alone before calling the lane red (it holds port 53682); a corrupt
>    incremental cache is retried with `-Pkotlin.incremental=false`. Manual checks are skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`; commit with a single `-m` sentence, one fix per commit. Then run:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`, amending on
>    the spot if either prints. Never branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
> then the final test result.

## Merging

Order **A, B, D, C**. In the main checkout: `git cherry-pick $START..<lane HEAD>`. Conflicts: `CLAUDE.md` files as a
word-level three-way merge keeping every sentence from both sides (root `CLAUDE.md`: plans 10 and 32;
`CampfireViewModel.kt`: plans 10 and 30). Run the unit tests after each lane; after the last, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit, never by rewriting landed commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan; `git status` clean; `git worktree list` shows only the checkout.
- Copy the plans' manual checks out before deleting anything; report commit count, skipped plans with reasons and the
  manual checks owed; update the `fourteenth-review` memory note.
- If only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.` Otherwise
  leave the skipped plans and the README trimmed to them. **Do not push.**
