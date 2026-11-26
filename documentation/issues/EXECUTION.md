# Executing the 61st review plans

The user starts this with *"Follow `documentation/issues/EXECUTION.md`."* The process is the `codebase-review` skill's
section 4; this is it for this sweep.

## Preconditions (stop and tell the user if one fails)

- The tree is clean apart from `documentation/issues/`.
- `README.md` records the challenge, and both decisions (01, 05) have answers there.
- `./gradlew desktopTest` passes.

Commit the plans if untracked (`Add the 61st review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane: `git worktree add --detach ../Campfire-lane-<x> $START`. Run at most two at once (Gradle).

| Lane | Plans, in order | Start |
|------|-----------------|-------|
| F | 04 | with C |
| C | 02 | with F |
| P | 01, 05 | when F or C is done |

## Lane prompt (fill in `<x>`, `<worktree>`, `<plans>`)

> You execute lane `<x>` of Campfire's 61st review in the worktree `<worktree>` (detached, cut from `START`). Plans, in
> order: `<plans>` (in `<worktree>/documentation/issues/`). Every command starts with `cd <worktree> &&` or uses
> `git -C <worktree>`. Scratch files go only in `<scratchpad>/lane-<x>/`. Never `git stash`, branch, merge, rebase,
> push, bump the version, or dispatch a workflow.
>
> For each plan:
> 1. Read the plan and the files it names. Load `code-style` before the first edit.
> 2. Implement exactly the plan, taking the recommended option or the one `README.md` records as decided. Find moved
>    code by the quoted snippet. If the plan is wrong or impossible, or its "drop this plan if" holds, revert the tree,
>    leave the plan file, note why, and go on.
> 3. Add the tests it asks for. Run a test that pins a defect once without the fix and see it fail, and say so only in
>    your report. Update the `CLAUDE.md` files and strings it names. A file past its size limit may not grow.
> 4. Verify with the tests of the touched modules (`./gradlew :<module>:desktopTest`, plus
>    `:tools:screenshots:test --tests '<the new test>'` for a UI test) and a compile of every platform the change
>    touches: desktop `:app:desktop:compileKotlin`, `androidMain` `:app:android:compileDebugKotlin`, the `expect`/`actual`
>    set also `:app:ios:linkDebugFrameworkIosSimulatorArm64` and `:app:web:compileKotlinWasmJs`.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`. Commit with one `-m` sentence, one plan per commit. Then run:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
>    amend on the spot if either prints. `git show --stat HEAD` must show only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`, then
> the final test result.

## Merging

In the order F, C, P, as lanes report:

1. List what each lane commit touched (`git -C <worktree> log --stat --format=%s $START..HEAD`) and stop on any path
   outside the lane's files.
2. In the main checkout, `git cherry-pick $START..<lane HEAD>`.
3. Run `./gradlew desktopTest` after each lane. After the last lane, run the full build:
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
   (run the web link alone if the combined build runs out of memory).
4. Fix a break with one more commit, then `git worktree remove ../Campfire-lane-<x>`.

## Finish

1. `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
2. `git status` is clean and `git worktree list` shows only the checkout.
3. Update the `61st-review` memory.
4. With no plan file left, delete this folder and commit `Remove the review plans.`

Do not push.
