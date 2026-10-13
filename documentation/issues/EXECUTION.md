# Executing the twenty-eighth review

Follow the `codebase-review` skill's section 4. This sweep is concrete as follows.

## Preconditions

- The tree is clean apart from `documentation/issues/`. The user's `documentation/TO_DO.md` change was uncommitted when
  the review ran. If it is still uncommitted, stop and ask the user what to do with it.
- The README's Challenge section is filled in.
- D1 is answered in the README.
- The unit tests pass:
  `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`

Then commit the plans (`Add the twenty-eighth review plans.`) and note `START=$(git rev-parse HEAD)`.

## Lanes

Run at most two lanes at once, since two Gradle builds is the cap. Start E and O first, because they are the longest.
Start C and T as those two finish.

- **E:** `git worktree add --detach ../Campfire-lane-e $START`. Plans 02, then 01.
- **O:** `git worktree add --detach ../Campfire-lane-o $START`. Plans 03, 04, 05, 06, 07, 08, 09, in that order.
- **C:** `git worktree add --detach ../Campfire-lane-c $START`. Plan 12.
- **T:** `git worktree add --detach ../Campfire-lane-t $START`. Plans 10, then 11.

## Subagent prompt (one per lane; fill in `<worktree>`, `<x>`, and the plan list)

> You carry out review plans in the Campfire repository, confined to the detached git worktree `<worktree>`.
>
> Plans, in order: `<plans>`, each at `<worktree>/documentation/issues/NN-*.md`. Read the README there for the lane's
> files and decisions: plan 11 follows D1's answer, not necessarily its recommended option.
>
> Rules:
> - Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`.
> - Scratch files go only to `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/`.
>   Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version, or dispatch a workflow.
> - Touch only your lane's files, as the README's Lanes table names them.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking its recommended option, and re-locate code by the quoted snippets. If the plan is
>    wrong or impossible, or its "drop this plan if" holds (plan 08: the label fits at 360dp), revert the working tree,
>    leave the plan file, note why, and go on.
> 3. Add its tests, and update the `CLAUDE.md` text and the strings it names. Strings go into both `values` and `values-hu`.
> 4. Verify:
>    - Lane C: `./gradlew :chordpro:desktopTest :presentation:desktopTest`
>    - Lane T: `./gradlew :tuner:api:desktopTest :tuner:implementation:desktopTest :app:android:compileDebugKotlin`.
>      Add `:presentation:desktopTest :app:desktop:compileKotlin` if strings or `TunerInputIssue` changed.
>    - Lanes E and O: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin`, and after the lane's last plan
>      `:app:android:compileDebugKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:compileKotlinWasmJs`
>    - `./gradlew spotlessCheck` on the touched modules, and `python3 .github/scripts/check_license_headers.py` for new files
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

Merge in the order C, T, E, O. For each lane:

1. Check the paths each lane commit touched with `git -C <worktree> log --stat --format=%s $START..HEAD`, and stop on
   any path outside the lane's files.
2. In the main checkout, run `git cherry-pick $START..<lane HEAD>`.
3. Run the unit tests.

After O, run the full build one task group at a time, since a combined invocation once OOMed in the web link:
`:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:app:web:wasmJsBrowserDistribution`,
and `:app:desktop:packageDistributionForCurrentOS`. A break gets one more commit. Then `git worktree remove` each lane's
worktree.

## Finish

Run the final checks from the skill: no attribution, one commit per plan, a clean status, and only the main checkout
in `git worktree list`.

Copy the manual checks owed out of the README, then update the `twenty-eighth-review` memory.

If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` Do not push.
