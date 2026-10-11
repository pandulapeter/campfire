# Executing the twenty-sixth review

You are the orchestrator. Follow this file; the plans and `README.md` are next to it. Repository:
`/Users/pandulapeter/Projects/Campfire`, branch `master`. Do not push.

## Preconditions (stop and tell the user if any fails)

1. `git status` is clean apart from `documentation/issues/`.
2. `README.md`'s Challenge section is filled in, and decision 04 is answered there. If the answer is "leave as is",
   delete plan 04 before committing the plans, and tell lane D that 06 then adds its own `rememberContinuousChange`
   without 04's.
3. The unit tests pass:
   `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`
4. If the plans are untracked, load `commit-messages` and commit them: `Add the twenty-sixth review plans.` Then
   `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order        | Cut from |
|------|------------------------|----------|
| T    | 20, 21, 22, 23         | `START`  |
| L    | 10, 11, 12, 13, 14     | `START`  |
| D    | 01, 02, 03, 04, 05, 06 | `START`  |

No more than two lanes may build at once. Start T and D first (D is the longest), then start L when T finishes.

For each lane: `git worktree add --detach ../Campfire-lane-<x> START`. Spawn one `general-purpose` subagent with
the prompt below, with `<x>`, `<WORKTREE>`, and `<PLANS>` filled in.

## Subagent prompt

> You carry out lane `<x>` of the twenty-sixth review in the detached worktree `<WORKTREE>`
> (`/Users/pandulapeter/Projects/Campfire-lane-<x>`). The plans, in this order, are `<PLANS>`, each at
> `<WORKTREE>/documentation/issues/NN-*.md`. Read `<WORKTREE>/documentation/issues/README.md` for the decisions.
>
> Isolation rules:
> - Every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`.
> - Scratch files go only to `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/3b457a1a-4d40-4787-87d9-8b20eebf071c/scratchpad/lane-<x>/`.
> - Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> For each plan:
> 1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, as amended under its `**Challenged:**` line, taking the recommended option (or the one
>    README.md's Decisions records). Re-locate moved code by the quoted snippet. If the plan is wrong or impossible,
>    or its own "drop this plan if" condition holds, do not improvise: revert the working tree, leave the plan file,
>    note why, and move on.
> 3. Add the tests it asks for, and update the CLAUDE.md sentences it names.
> 4. Verify:
>    - the desktopTest of every module touched;
>    - a compile of every platform touched: `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for
>      `:presentation`), `:app:android:compileDebugKotlin` for `androidMain`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`
>      for `iosMain`, and `:app:web:compileKotlinWasmJs` for `wasmJsMain` (plan 23, and any `:presentation` or
>      `:metronome`/`:tuner` change, since `commonMain` compiles to every target);
>    - `./gradlew spotlessCheck`, and `python3 .github/scripts/check_license_headers.py` for any new file.
>
>    Retry a corrupt incremental cache with `-Pkotlin.incremental=false`. Skip manual checks and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages` and commit with a single `-m` sentence, one fix per commit. Then run:
>    ```
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
>    git show --stat HEAD
>    ```
>    Amend on the spot if either check prints, or if the commit touched files outside the plan's list.
> 7. Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
>    then the final test result.

## Merging

Merge in the order **T, L, D**, as lanes report:

1. Run `git -C ../Campfire-lane-<x> log --stat --format=%s START..HEAD`. Stop on any path outside the lane's files
   (README's Lanes table).
2. In the main checkout, run `git cherry-pick START..<lane HEAD>`. Resolve a CLAUDE.md conflict as a word-level
   three-way merge, keeping every sentence from both sides.
3. Run the unit tests (above). Fix a break with one more commit, never by rewriting landed commits.
4. Run `git worktree remove ../Campfire-lane-<x>`.

After D has merged, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline START..HEAD` shows one line per plan.
- `git status` is clean, and `git worktree list` shows only the checkout.
- Copy the manual checks out of README.md, then report the commit count, the skipped plans with reasons, and the
  manual checks owed. Update the `twenty-sixth-review` memory.
- If only `README.md` and `EXECUTION.md` are left, delete `documentation/issues/` and commit `Remove the review plans.`
  If skipped plans remain, leave the folder, trim README.md to them, and ask the user.
