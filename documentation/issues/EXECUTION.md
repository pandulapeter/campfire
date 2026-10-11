# Executing the twenty-fifth review

You are the orchestrator. Follow this file; the plans and `README.md` are next to it. Repository:
`/Users/pandulapeter/Projects/Campfire`, branch `master`. Do not push.

## Preconditions (stop and tell the user if any fails)

1. `git status` is clean apart from `documentation/issues/`.
2. `README.md`'s Challenge section is filled in, and both decisions (14, 23) are answered there.
3. The unit tests pass:
   `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`
4. If the plans are untracked, load `commit-messages` and commit them: `Add the twenty-fifth review plans.` Then
   `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order        | Cut from                                   |
|------|------------------------|--------------------------------------------|
| S    | 01                     | `START`                                    |
| M    | 22, 23, 21, 20         | `START`                                    |
| L    | 10, 11, 12, 13, 14, 15 | `START`                                    |
| U    | 30, 31, 32             | `START`                                    |
| Z    | 40, 41, 42, 43, 45, 46 | the main checkout's `HEAD` after S, M, L and U merged |

At most two lanes building at once: start S and M, then L and U as they finish, then Z.

For each lane: `git worktree add --detach ../Campfire-lane-<x> <base>`. Spawn one `general-purpose` subagent with
the prompt below, `<x>`, `<WORKTREE>` and `<PLANS>` filled in.

## Subagent prompt

> You carry out lane `<x>` of the twenty-fifth review in the detached worktree `<WORKTREE>`
> (`/Users/pandulapeter/Projects/Campfire-lane-<x>`). Plans, in this order: `<PLANS>`, each at
> `<WORKTREE>/documentation/issues/NN-*.md`. Read `<WORKTREE>/documentation/issues/README.md` for the decisions.
>
> Isolation rules:
> - Every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`.
> - Scratch files go only to `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/1abb7e2d-45f8-4221-90c0-15d14aa0a061/scratchpad/lane-<x>/`.
> - Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Per plan:
> 1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking the recommended option (or the option README.md's Decisions records). Re-locate
>    moved code by the quoted snippet. If the plan is wrong, impossible, or its own "drop this plan if" condition
>    holds, do not improvise: revert the working tree, leave the plan file, note why, and move on.
> 3. Add the tests it asks for; update the CLAUDE.md files it names, changing only the sentences it quotes.
> 4. Verify:
>    - the desktopTest of every module touched;
>    - a compile of every platform touched: `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for
>      `:presentation`), `:app:android:compileDebugKotlin` for `androidMain`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`
>      for `iosMain`, `:app:web:compileKotlinWasmJs` for `wasmJsMain`; `:tools:screenshots:compileKotlin` for plan 23;
>    - `./gradlew spotlessCheck` and `python3 .github/scripts/check_license_headers.py` for any new file.
>    `DesktopSyncAuthenticatorTest` holds port 53682; rerun it alone before calling the lane red. Retry a corrupt
>    incremental cache with `-Pkotlin.incremental=false`. Skip manual checks and report them.
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

In the order **S, M, L, U, Z**, as lanes report:

1. `git -C ../Campfire-lane-<x> log --stat --format=%s <base>..HEAD`. Stop on any path outside the lane's files
   (README's Lanes table).
2. In the main checkout: `git cherry-pick <base>..<lane HEAD>`. Resolve a CLAUDE.md conflict as a word-level
   three-way merge, keeping every sentence from both sides.
3. Run the unit tests (above). Fix a break with one more commit, never by rewriting landed commits.
4. `git worktree remove ../Campfire-lane-<x>`.

After U merged, cut lane Z from `HEAD`. After Z merged, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline START..HEAD` shows one line per plan.
- `git status` is clean, and `git worktree list` shows only the checkout.
- Copy the manual checks out of README.md, then report: the commit count, the skipped plans with reasons, and the
  manual checks owed. Update the `twenty-fifth-review` memory.
- If only `README.md` and `EXECUTION.md` are left, delete `documentation/issues/` and commit `Remove the review plans.`
  If skipped plans remain, leave the folder, trim README.md to them, and ask the user.
