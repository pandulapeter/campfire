# Executing the 33rd review's plans

You are the orchestrating agent. Follow `.claude/skills/codebase-review/SKILL.md` section 4. This file fills it in
for this sweep.

## Preconditions

Stop and tell the user if any of these fails:

- The tree is clean apart from `documentation/issues/`.
- Every plan carries its `**Challenged:**` line.
- Both Decisions in `README.md` have answers (they do: the wider pill for 06; restore small texts, plan 09).
- The unit tests pass (the root `CLAUDE.md`'s Tests command).

If the plans are untracked, commit them first as `Add the 33rd review plans.`, then record
`START=$(git rev-parse HEAD)`.

## Lanes

Each lane gets one detached worktree next to the checkout: `git worktree add --detach ../Campfire-lane-<x> <base>`.
Run at most two Gradle builds at once.

| Lane | Base | Plans, in order | Starts |
|------|------|-----------------|--------|
| D | `START` | 05 | at once |
| S | `START` | 01, 02, 09 | at once |
| U | `START` | 06, 07, 08 | when D or S has finished |
| I | `master` after S is merged | 03 | after S merges; its cherry-pick range starts at that base |

## Subagent prompt (one `general-purpose` agent per lane; fill in `<worktree>`)

> You carry out review plans in the Campfire repo, in the detached git worktree `<worktree>`, and nowhere else.
>
> **Your plans**, in this order: `<lane's plan files, under documentation/issues/>`. Read `README.md` there first for
> the decisions taken.
>
> **Rules:**
> - Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`.
> - Scratch files go to `<scratchpad>/lane-<x>/` only. Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> **Per plan:**
> 1. Read the plan and every file it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking its recommended option. Re-locate moved code by the quoted snippet.
> 3. If the plan is wrong, impossible, or its own "drop this plan if" condition holds, do not improvise. Revert the
>    working tree, leave the plan file, note why, and go to the next plan.
> 4. Add the tests it asks for, and update the `CLAUDE.md` files it names.
> 5. Verify with the tests of the touched modules (from the root `CLAUDE.md`'s Tests command) and a compile of every
>    platform the change touches:
>    - desktop: `:app:desktop:compileKotlin`
>    - Android: `:app:android:compileDebugKotlin`
>    - iOS: `:app:ios:linkDebugFrameworkIosSimulatorArm64`
>    - web: `:app:web:compileKotlinWasmJs`
>
>    Also run `spotlessCheck` and, for lane U, `:tools:screenshots:test`.
>
>    If a golden changes, do not re-record it on a Mac. Report it as `golden changed: <name>`.
> 6. `git rm` the plan file in the same commit as its fix.
> 7. Load the `commit-messages` skill. Commit with a single `-m` sentence: one plan, one commit. Then run these checks
>    and amend if one fails:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo ATTRIBUTION`.
>    Finally, `git show --stat HEAD` must list only that plan's files.
>
> **Known false alarms:**
> - `DesktopSyncAuthenticatorTest` holds port 53682; rerun it alone before calling the lane red.
> - On a corrupt incremental cache, retry with `-Pkotlin.incremental=false`.
>
> **Report:**
> - one line per plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`;
> - one line per skipped plan: `Not done: NN — <why>`;
> - the final test result.

## Merging, in order D, S, U, I

For each lane, as it reports:

1. Run `git -C ../Campfire-lane-<x> log --stat --format=%s <base>..HEAD`. Stop on any path outside the lane's files
   (README table).
2. In the main checkout, run `git cherry-pick <base>..<lane HEAD>`. Resolve `CLAUDE.md` and `strings.xml` conflicts
   word by word, keeping both sides.
3. Run the unit tests.
4. Run `git worktree remove ../Campfire-lane-<x>`.

Lane I is created only after S has merged.

After the last lane, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
If the combined build runs out of memory, run the web link on its own. Fix a break with one more commit; never rewrite
landed commits.

Goldens lane U reported as changed are re-recorded on Linux (CI) per `tools/screenshots/CLAUDE.md`. Name them to the
user.

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline START..HEAD` shows one line per plan.
- `git status` is clean.
- `git worktree list` shows only the checkout.

Report the commit count and any skipped plans with their reasons. Name a hand check only for 03, the iOS kill-and-reopen
check in its plan. Update the memory (`review-history.md`, the 33rd line).

If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.`. Do not push.
