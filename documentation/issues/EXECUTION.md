# Executing the 57th review's plans

The owner starts this with *"Follow `documentation/issues/EXECUTION.md`."* The process is the `codebase-review`
skill's section 4; this file fills it in for this sweep.

## Preconditions (stop and tell the owner if any fails)

- `git status` is clean apart from `documentation/issues/`.
- Every plan carries a `**Challenged:**` line (all six do).
- Every decision in `README.md` marked *awaiting the owner* has an answer recorded there.
- `./gradlew desktopTest` passes.

Then commit the folder (`Add the 57th review plans.`) and note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane, next to the checkout: `git worktree add --detach ../Campfire-lane-<x> $START`.
At most two lanes build at once (the Kotlin daemon runs out of memory with more on the 24 GB Mac).

| Lane | Plans, in order | Starts from                          |
|------|-----------------|--------------------------------------|
| F    | 03              | `START`                              |
| R    | 04              | `START`                              |
| S    | 05              | `START`                              |
| H    | 01              | `START`                              |
| P    | 06, 07          | the main checkout's `HEAD` once H is merged (both edit `ExportController.kt`) |

Suggested scheduling: F and R together; then S and H; then P after H is merged.

## Lane prompt (fill in `<x>`, `<worktree>`, `<plans>`)

> You execute the 57th review's plans `<plans>` in the detached worktree `<worktree>` of Campfire (Kotlin
> Multiplatform). Confine yourself to it: every command starts with `cd <worktree> &&` or uses `git -C <worktree>`.
> Scratch files go only to `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/`;
> never run a script from another lane's folder. Never `git stash`, branch, merge, rebase, push, bump the version, or
> dispatch a workflow. `local.properties` is not in the worktree; no plan here needs it.
>
> Per plan, in order:
> 1. Read the plan and the files it names, and the nearest `CLAUDE.md`. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking the recommended option (and the owner's answers in `documentation/issues/README.md`).
>    Re-locate moved code by the quoted snippets. If the plan is wrong or impossible, or its "drop this plan if"
>    condition holds, do not improvise: revert the tree, leave the plan file, note why, and go on.
> 3. Add the tests it asks for. A test that pins a defect is run once without the fix and seen failing (say so only in
>    your report). Update the `CLAUDE.md` files and strings it names. A file already past its size limit may not end a
>    commit longer than it began.
> 4. Verify: the touched modules' `desktopTest`, and a compile of every platform the change touches: desktop
>    `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`); `androidMain` →
>    `:app:android:compileDebugKotlin`; `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64` (plus
>    `:data:source:local:implementation:iosSimulatorArm64Test` for plan 05); `wasmJsMain` → `:app:web:compileKotlinWasmJs`
>    (plus `:data:source:local:implementation:wasmJsBrowserTest` if the storage contract changes). A corrupt
>    incremental cache is a known false alarm: retry with `-Pkotlin.incremental=false`.
> 5. `git rm` the plan file in the same commit as its fix (a plan that lands as two commits, like 05's bug fix and then
>    its refactor, removes the file in the last one).
> 6. Load the `commit-messages` skill; commit with a single `-m` sentence, one fix per commit. Then check and amend on
>    the spot if either prints:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"`
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`
>    `git show --stat HEAD` must list only that plan's files.
> 7. Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
>    then the final test result.

Lane-specific notes:
- **S (05):** the copy bug's fix lands first as its own commit, with the simulator contract test written first and seen
  failing; the refactor follows. Apply the owner's answer on reconciling marks at launch.
- **H (01):** the parity test (`SharedCopyParityTest`, desktopTest) must pass at 200 seeds.
- **P (06, 07):** cut from `HEAD` after lane H merged. `ExportControllerTest` and `PdfRoundTripTest` must pass unchanged
  after 06.

## Merging

In order **F, R, S, H, P**, as lanes report. First list what each lane commit touched
(`git -C <worktree> log --stat --format=%s <base>..HEAD`) and stop on any path outside the lane's files (README's
Lanes table). Then, in the main checkout, `git cherry-pick <base>..<lane HEAD>`. Resolve conflicts keeping both sides'
intent; `CLAUDE.md` paragraphs as a word-level three-way merge. Run `./gradlew desktopTest` after each lane; a break is
fixed by one more commit, never by rewriting landed ones. Then `git worktree remove ../Campfire-lane-<x>`.

After the last lane, the full build (run the web distribution on its own if the combined build runs out of memory):

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS
./gradlew :app:web:wasmJsBrowserDistribution
./gradlew :tools:screenshots:test
```

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline $START..HEAD` is one line per fix, `git status` is clean, `git worktree list` shows only the checkout.
- Report the commit count and the skipped plans with reasons. Update the `57th-review` memory.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.` Do not push.
