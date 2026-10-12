# Executing the twenty-seventh review

Follow the `codebase-review` skill's section 4. This sweep is concrete as follows.

## Preconditions

- The tree is clean apart from `documentation/issues/`.
- The README's Challenge section is filled in.
- D1 is answered in the README (count the gaps).
- The unit tests pass:
  `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`

Then commit the plans (`Add the twenty-seventh review plans.`) and note `START=$(git rev-parse HEAD)`.

## Lanes

Both lanes run at once; that is two Gradle builds, which is the cap.

- **C:** `git worktree add --detach ../Campfire-lane-c $START`. Plan 03.
- **H:** `git worktree add --detach ../Campfire-lane-h $START`. Plans 01, 02, 05, 04, 06, in that order.

## Subagent prompt (one per lane; fill in `<worktree>`, `<x>`, and the plan list)

> You carry out review plans in the Campfire repository, confined to the detached git worktree `<worktree>`.
>
> Plans, in order: `<plans>`, each at `<worktree>/documentation/issues/NN-*.md`. Read the README there for decisions:
> plan 04 follows D1's answer.
>
> Rules:
> - Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`.
> - Scratch files go only to `<scratchpad>/lane-<x>/`. Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking its recommended option, and re-locate code by the quoted snippets. If the plan is
>    wrong or impossible, or its "drop this plan if" holds (plan 03: the new test passes on the unchanged code), revert
>    the working tree, leave the plan file, note why, and go on.
> 3. Add its tests, and update the `CLAUDE.md` text it names.
> 4. Verify:
>    - `./gradlew :presentation:desktopTest :app:desktop:compileKotlin`
>    - `:app:android:compileDebugKotlin`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, and
>      `:app:web:compileKotlinWasmJs` for plan 01, since it changes Compose effect wiring in `commonMain`
>    - `./gradlew spotlessCheck` on the touched module
>
>    If an incremental cache is corrupt, retry with `-Pkotlin.incremental=false`. Skip manual checks and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill. Commit with one `-m` sentence in the repo's voice, one plan per commit. Then run:
>    ```
>    git show --stat HEAD   # only this plan's files
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
>    ```
>    Amend on the spot if either check prints.
>
> Report per plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what>`. Then report
> `Not done: NN — <why>`, then the final test result.

## Merge

Merge C, then H. For each lane:

1. Check the paths each lane commit touched with `git -C <worktree> log --stat --format=%s $START..HEAD`, and stop on
   any path outside the lane's files.
2. In the main checkout, run `git cherry-pick $START..<lane HEAD>`.
3. Run the unit tests.

After H, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break gets one more commit. Then `git worktree remove ../Campfire-lane-<x>` for both lanes.

## Finish

Run the final checks from the skill: no attribution, one commit per plan, a clean status, and only the main checkout
in `git worktree list`.

Copy the manual checks owed out of the README, then update the `twenty-seventh-review` memory.

If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` Do not push.
