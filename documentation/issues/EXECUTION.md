# Executing the thirteenth review

The user starts this with *"Follow `documentation/issues/EXECUTION.md`."* Read `README.md` next to this file first:
it holds the decisions (answered before execution starts), the lanes and the shared-file rules.

## Preconditions — stop and tell the user if any fails

1. `git status --short` shows nothing but `documentation/issues/`.
2. Every plan carries a `**Challenged:**` line, and none of the plans still present says `dropped`.
3. Every entry under README's **Decisions** is marked answered.
4. The unit tests pass on the starting commit:
   `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest`

Then, if the plans are untracked, load the `commit-messages` skill and commit them as `Add the thirteenth review plans.`,
and note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane next to the checkout (no branch is created):
`git worktree add --detach ../Campfire-lane-<x> $START`. At most **two lanes run Gradle at the same time** (Kotlin
daemon OOMs on the 24 GB Mac): start D and B together, C when the first of them reports, A when the next one does.

| Lane | Area | Plans, in this order |
|---|---|---|
| D | release skill, docs, What's new recording | 45, 46, 47 |
| B | setlists, import report | 20, 22, 23, 24, 21 |
| C | song details, editor, Songs list, filter chips | 30, 31, 32, 33, 34, 35, 36, 37, 38 |
| A | bottom sheets, forms, text input | 01, 02, 04, 14, 03, 05, 06, 07, 08, 09, 12, 10, 11, 13 |

Skip any plan whose file says `**Challenged:** dropped`, or that README lists as removed by a decision.

## The lane prompt

Spawn one `general-purpose` subagent per lane with this prompt, filling in `<LANE>`, `<PLANS>` and `<WORKTREE>`:

> You carry out lane `<LANE>` of the thirteenth Campfire review in the worktree `<WORKTREE>` (detached HEAD; never
> create a branch, merge, rebase, push, bump the version or dispatch a workflow). Work only inside that worktree. Read
> `<WORKTREE>/CLAUDE.md`, `<WORKTREE>/presentation/CLAUDE.md` and `<WORKTREE>/documentation/issues/README.md` (the
> decisions taken and the shared-file rules), then do the plans `<PLANS>` in that order. For each plan:
>
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking the recommended option or the one README's Decisions chose. Find code that
>    has moved by the quoted snippet. If the plan is wrong, impossible, or its own "drop this plan if" condition
>    holds, do **not** improvise another fix: revert the working tree (`git checkout -- .` and remove new files), leave
>    the plan file, note why, and go on to the next plan.
> 3. Add the tests the plan asks for; update the `CLAUDE.md` files and both `strings.xml` files it names (every new
>    key in `values/` and `values-hu/`).
> 4. Verify: the unit test command below, plus a compile of every platform the change touches — desktop
>    `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`), `androidMain` →
>    `:app:android:compileDebugKotlin`, `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `wasmJsMain` →
>    `:app:web:compileKotlinWasmJs`. A `commonMain` change in `:presentation` or `:chordpro` compiles on desktop and
>    on wasmJs at least. Manual checks that need a device, an account, a store or a network condition are skipped
>    and reported. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling the lane red. A
>    corrupt incremental cache is retried with `-Pkotlin.incremental=false`.
>    Unit tests: `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest`
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill and commit with a single `-m` sentence in the repo's voice (e.g. `Head a
>    recalled chorus by the section it repeats.`) — one plan, one commit. Then check:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`, and amend on
>    the spot if either prints.
>
> Finish with a lane report: one line per plan, `NN: <hash> "<message>" — verified: <commands>; skipped manual:
> <what, why>`, then `Not done: NN — <why>` for every plan you skipped, then the final unit test result.

## Merging

In the main checkout, in this order as the lanes report: **D, B, C, A** — D is small and touches the view model's
What's new functions that A's plan 06 sits next to; B's view-model edits come before C and A build on the same
file; A touches the most shared UI files (`Dialogs.kt`, both `strings.xml`, `:chordpro`, `presentation/CLAUDE.md`)
and goes last.

`git cherry-pick $START..<lane HEAD>` for each. Resolve conflicts keeping both sides' intent: `CLAUDE.md` paragraphs
and `strings.xml` as a word-level three-way merge, every sentence and key from both sides kept, never one side's
version whole. Run the unit tests after each lane, and the full build after the last:

`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

A break is fixed by one more commit (`Fix the web build after the sheet changes.`), never by rewriting landed commits.
Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- Plan files still present are the skipped ones: leave them, trim README to them, or ask the user whether to delete
  the folder.
- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan (plus any build-fix commits); `git status` is clean;
  `git worktree list` shows only the checkout.
- Report the commit count, the skipped plans with reasons, and the manual checks owed — copy them out of README
  first. Update the memory note `thirteenth-review.md` with the commit range, what was skipped and the manual checks.
- When no plan file is left (only `README.md` and `EXECUTION.md`), delete `documentation/issues/` and commit it as
  `Remove the review plans.` without asking. **Do not push.**
