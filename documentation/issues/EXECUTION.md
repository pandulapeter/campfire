# Executing the 53rd review plans

Follow `.claude/skills/codebase-review/SKILL.md` section 4. What is specific to this sweep:

## Preconditions

The tree is clean apart from `documentation/issues/`; the README records the challenge; D1 and D2 have answers;
`./gradlew desktopTest` passes. Commit the plans (`Add the 53rd review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

- **A** — `git worktree add --detach ../Campfire-lane-a $START`; plans 01 → 02 (02 only if D1 is (a); if D1 is (b),
  carry out (b) as the plan describes it, or drop the plan if the user said to leave it).
- **B** — `git worktree add --detach ../Campfire-lane-b $START`; plans 03 → 04 → 05 → 06 → 07 → 08 → 09.

Both lanes may build at once (two Gradle builds is the cap).

## Lane prompt (fill in `<worktree>` and `<x>`)

> You carry out the plans of lane `<x>` of the 53rd Campfire review, in the detached worktree `<worktree>`, in this
> order: `<plans>`. Read `documentation/issues/README.md` and each plan in full before starting it. Follow
> `.claude/skills/codebase-review/SKILL.md` section 4, "Per plan": load `code-style` before the first edit, implement
> exactly the plan's recommended option (re-locate code by the quoted snippets), add its tests, update the `CLAUDE.md`
> files it names, verify, `git rm` the plan file in the same commit, load `commit-messages`, commit with one `-m`
> sentence, then run the two commit checks. Rules: every command starts with `cd <worktree> &&` or uses
> `git -C <worktree>`; scratch files only in `<scratchpad>/lane-<x>/`, and never run a script you did not write; after
> every commit, `git show --stat HEAD` must list only that plan's files; never `git stash`, branch, merge, rebase, push,
> or bump the version. No test is `@Ignore`d; a defect a new test exposes is fixed in its own commit first, or the plan
> stops and is reported. A plan that is wrong or whose "drop this plan if" holds is reverted and left in place, with
> why. Report per plan: `NN: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final
> test result.

## Verification

- Lane A: `./gradlew :chordpro:desktopTest :presentation:desktopTest :app:desktop:compileKotlin`.
- Lane B: `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest` (the `DocumentGoldenTest`
  fixtures `songbook-styled` and `songbook-numbered` must not change).
- `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling a lane red.

## Merge and finish

Cherry-pick A, then B, into the main checkout after checking each lane's paths; `./gradlew desktopTest` after each,
then the full build (`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64
:app:desktop:packageDistributionForCurrentOS`, then `:app:web:wasmJsBrowserDistribution` alone). Remove the
worktrees, run the final checks of section 4, update the `53rd-review` memory, and when no plan file is left remove the
folder (`Remove the review plans.`). Do not push.
