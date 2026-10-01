# Executing the eleventh review

Follow the `codebase-review` skill's section 4 (load it). Concretely:

## Preconditions
Stop and tell the user if any fails: `git status` clean apart from `documentation/issues/`; the README's Challenge
section is filled in; no open decisions; the unit tests pass:
`./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest`.
Commit the plans if untracked (`Add the eleventh review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes
`git worktree add --detach ../Campfire-lane-<x> $START` for x in a, b, c. At most two Gradle builds at once: start B
and A, then C when B reports.

- **B:** 05
- **A:** 01, 02, 03, 04
- **C:** 07, 09, 06, 08

## Subagent prompt (one per lane, `general-purpose`)

> You work only in `<WORKTREE>` (a detached worktree of the Campfire repo; never touch the main checkout). Carry out
> these plans from `documentation/issues/`, in this order: `<PLANS>`. For each: read the plan and every file it
> names; load the `code-style` skill before the first edit; implement exactly the plan, taking the recommended option,
> finding moved code by the quoted snippet. If the plan is wrong, impossible, or its own "drop this plan if" holds, do
> not improvise: revert the working tree, leave the plan file, note why, move on. Add the tests it asks for, update the
> CLAUDE.md files and both `strings.xml` files it names. Verify: the module's desktop tests
> (`:chordpro:desktopTest` or `:presentation:desktopTest`) and compile `:app:desktop:compileKotlin`; for lane C also
> `:app:android:compileDebugKotlin` and `:app:web:compileKotlinWasmJs`. Skip and report manual checks.
> `git rm` the plan file in the same commit as its fix. Load `commit-messages`, commit with a single `-m` sentence in
> the repo's voice (e.g. `Leave a bracket unchanged unless it is a whole chord name.`), one fix per commit, then run
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`, amending on the
> spot if either prints. Never branch, merge, rebase, push, bump the version or dispatch a workflow. If
> `DesktopSyncAuthenticatorTest` fails, rerun it alone; a corrupt incremental cache is retried with
> `-Pkotlin.incremental=false`. Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what>`, then
> `Not done: NN — <why>`, then the final test result.

## Merge
In the main checkout, in order **B, A, C**: `git cherry-pick $START..<lane HEAD>`; resolve `presentation/CLAUDE.md`
and `strings.xml` conflicts word by word keeping both sides. Unit tests after each lane; after C the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish
`git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0; one commit per plan;
`git status` clean; `git worktree list` shows only the checkout. Copy the README's manual checks into the
`eleventh-review` memory note, then, if no plan file is left, delete `documentation/issues/` and commit
`Remove the review plans.` Do not push.
