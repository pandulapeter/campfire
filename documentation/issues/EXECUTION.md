# Execution brief: fifteenth review

Started with *"Follow `documentation/issues/EXECUTION.md`."* Read `README.md` first; the plans are the `NN-*.md`
files beside it. Load the `codebase-review` skill for the general procedure; this file is its section 4 made concrete.

## Preconditions (stop and tell the user if any fails)

- `git status` is clean apart from `documentation/issues/` (the user's uncommitted `SKILL.md`, `TO_DO.md` and
  `strings.xml` edits from the review day must be committed or set aside by the user — never by you).
- The challenge has run and every decision is answered (README, 2026-10-05): 02 A, 03 A, 24 yes, 36 **B (docs only,
  no code)**, 38 point 7 KDoc only, 01 `{capo}` last-wins. Each decided plan carries a `**Decided:**` line.
- The unit tests pass:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`
- If the plans are untracked, commit them first: `Add the fifteenth review plans.` Then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans in order | Compile checks besides the tests |
|------|----------|----------------|----------------------------------|
| A | `../Campfire-lane-a` | 01, 02, 03 | `:chordpro:desktopTest`, `:app:desktop:compileKotlin` |
| B | `../Campfire-lane-b` | 10, 11, 12, 13, 14, 15 | `:data:repository:implementation:desktopTest`, `:app:desktop:compileKotlin` |
| C | `../Campfire-lane-c` | 20, 21, 22, 23, 24, 25 | `:metronome:api:desktopTest :metronome:implementation:desktopTest`, `:app:android:compileDebugKotlin` (20), `:app:web:compileKotlinWasmJs` (21, 22, 24, 25), `:app:desktop:compileKotlin` (22, 24), `:app:ios:linkDebugFrameworkIosSimulatorArm64` (23, 24) |
| D | `../Campfire-lane-d` | 30, 31, 32, 33, 34, 35, 36, 37, 38 | `:presentation:desktopTest`, `:app:desktop:compileKotlin` |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. **At most two concurrent Gradle builds**: start A and
B together (both short), then C and D as they finish. No lane needs `local.properties`.

## Subagent prompt (one `general-purpose` agent per lane; fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`, `<CHECKS>`)

> You are executing lane <LANE> of the fifteenth Campfire review, confined to the worktree `<WORKTREE>` (a detached
> checkout; never touch `/Users/pandulapeter/Projects/Campfire` itself). Read `documentation/issues/README.md` (shared
> file rules, decisions) and then, in this order, the plans <PLANS>. For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking the recommended option or the one the README's Decisions record. Re-locate
>    moved code by the quoted snippet. If the plan is wrong, impossible, or its "drop this plan if" condition holds, do
>    not improvise: revert the working tree, leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the CLAUDE.md files and strings it names (strings in both `values/` and
>    `values-hu/`).
> 4. Verify: the lane's tests and compile checks — <CHECKS>. Rerun `DesktopSyncAuthenticatorTest` alone before calling
>    a run red (it holds port 53682); retry a corrupt incremental cache with `-Pkotlin.incremental=false`. Skip manual
>    checks that need a device, account or network, and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill; commit with one `-m` sentence in the repo's voice (one fix, one commit). Then:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"` — amend on the
>    spot if either prints.
> Never branch, merge, rebase, push, bump the version or dispatch a workflow.
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>` per plan, then
> `Not done: NN — <why>`, then the final test result.

## Merging (main checkout, in this order: A, B, C, D)

`git cherry-pick $START..<lane HEAD>` as each lane reports, in merge order (hold a lane that finishes early until the
ones before it are in). Conflicts in `CLAUDE.md` files are a word-level three-way merge keeping every sentence of both
sides (see the README's shared-file rules). Run the unit tests after each lane; after D the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
and the web Node tests (`cd app/web && node --test tests/*.cjs`). A break is fixed by one more commit, never by
rewriting landed commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan (plus any build fix); `git status` clean; `git worktree list`
  shows only the checkout.
- Copy the README's "Manual checks owed" into the memory note `fifteenth-review.md` with the commit range and skipped
  plans.
- If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` Otherwise leave the
  skipped ones and ask. **Do not push.**
