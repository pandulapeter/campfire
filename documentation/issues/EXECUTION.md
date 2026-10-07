# Executing the twenty-first review

The user starts this with *"Follow `documentation/issues/EXECUTION.md`."* Follow the `codebase-review` skill's
section 4; this is that section made concrete for this sweep.

## Preconditions (stop and tell the user if any fails)

1. `git status` is clean apart from `documentation/issues/`.
2. Every plan has a `**Challenged:**` line (all eight do).
3. The decisions in `README.md` are answered (they are). Plan 31 takes option A (restore), and plan 33 takes a+b.
4. The unit tests pass:
   `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`
   (`DesktopSyncAuthenticatorTest` holds port 53682; rerun it alone before calling anything red).

Then commit the plans if they are untracked (`Add the twenty-first review plans.`) and note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane, created from START:
`git worktree add --detach ../Campfire-lane-<x> $START`.

At most **two Gradle builds at once**. Start T and P together; start S when the first of them reports, and E when the
next one does.

| Lane | Worktree | Plans, in order | Verify |
|------|----------|-----------------|--------|
| T | `../Campfire-lane-t` | 20, 21 | `:presentation:desktopTest :data:source:local:implementation:desktopTest` |
| P | `../Campfire-lane-p` | 01, 02 | `:presentation:desktopTest :app:desktop:compileKotlin` |
| S | `../Campfire-lane-s` | 10 | `:presentation:desktopTest :app:desktop:compileKotlin :app:android:compileDebugKotlin` |
| E | `../Campfire-lane-e` | 32, 33, 31 | `:presentation:desktopTest :app:desktop:compileKotlin :app:desktop:createReleaseDistributable` (33 touches `app/desktop`'s Java/Kotlin and the common view model) and `:app:android:compileDebugKotlin` |

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`, `<VERIFY>`)

> You carry out review plans in the Campfire repo. Work ONLY inside `<WORKTREE>` (a detached git worktree; never
> touch `/Users/pandulapeter/Projects/Campfire` except where a plan names an absolute path there). Lane `<LANE>`,
> plans in this order: `<PLANS>`, each at `<WORKTREE>/documentation/issues/NN-*.md`. Read
> `documentation/issues/README.md`'s Decisions section first; where a plan offers options, take the one the README
> records as the user's answer.
>
> For each plan, in order:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan. Re-locate code by the quoted snippets, since line numbers may have moved. If the plan
>    is wrong or impossible, or its "drop this plan if" condition holds, do not improvise: `git checkout -- .`, leave
>    the plan file, note why, and go on.
> 3. Add the tests it asks for, and update the `CLAUDE.md` files it names.
> 4. Verify: `./gradlew <VERIFY>` from `<WORKTREE>`. A corrupt incremental cache is retried with
>    `-Pkotlin.incremental=false`. Manual checks that need a device, an account or a store are skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill. Commit with a single `-m` sentence: one fix per commit, never two folded
>    together and never one split. Then check the commit and amend it at once if either line prints:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"`
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`
>    Never branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Report one line per plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`. Then
> `Not done: NN — <why>`, then the final test result.

## Merging

Merge in the order **T, P, S, E**, as the lanes report. In the main checkout:
`git cherry-pick $START..<lane HEAD>`.

When a cherry-pick conflicts:
- `presentation/CLAUDE.md` and `app/desktop/CLAUDE.md` are merged word by word, three ways, keeping every sentence
  from both sides.
- Code conflicts keep both intents.

Run the unit tests after each lane. After the last one, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

A break is fixed with one more commit, never by rewriting landed commits. Then
`git worktree remove ../Campfire-lane-<x>`.

## Finish

1. Run the final checks:
   - `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
   - `git log --oneline $START..HEAD` shows one line per plan, plus any build fix;
   - `git status` is clean;
   - `git worktree list` shows only the checkout.
2. Copy "Manual checks owed" out of the README, then update
   `/Users/pandulapeter/.claude-personal/projects/-Users-pandulapeter-Projects-Campfire/memory/twenty-first-review.md`
   with:
   - the commit range;
   - the skipped plans;
   - the manual checks owed.
3. If only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`. If
   skipped plans remain, leave the folder and ask the user.

**Do not push.**
