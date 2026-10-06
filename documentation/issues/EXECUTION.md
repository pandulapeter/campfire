# Executing the nineteenth review's plans

Started with *"Follow `documentation/issues/EXECUTION.md`."* Read `README.md` here first, and load the
`codebase-review` skill (its section 4 is the procedure this restates).

## Preconditions — stop and tell the user if any fails

- The tree is clean apart from `documentation/issues/` and the user's own `documentation/TO_DO.md` edit. **Leave
  `TO_DO.md` unstaged** in every commit.
- The README's Decisions D-01, D-10 and D-40 have answers recorded. A plan the user declined is deleted before the
  plans are committed and removed from the README's index.
- The challenge ran (the README says so).
- The unit tests pass at HEAD.

Then commit the plans (`git add documentation/issues && git commit -m "Add the nineteenth review plans."`, the
`commit-messages` skill loaded) and note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans in order |
|------|----------|----------------|
| E | `../Campfire-lane-e` | 20, 21, 22 |
| A | `../Campfire-lane-a` | 01 |
| P | `../Campfire-lane-p` | 30, 31, 10 |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. Start A and P first (two Gradle builds at most at
once); start E when one finishes — E needs no Gradle build beyond `plutil -lint` and a YAML parse.

**Lane W (plan 40) is yours, not a subagent's**, and only if D-40 says to apply it: make the three exact text replacements
in `../CampfireWebsite/privacy/index.html`, leaving that repository's index and every other file untouched (it holds the
user's own work), then `git rm documentation/issues/40-*.md` and commit that removal alone in this repository as
`Note the privacy policy's sync paragraph for the website.`. Report that the website edit is uncommitted, for the user.

## Subagent prompt (fill in `<worktree>`, `<lane>`, `<plans>`)

> You execute review plans in the Campfire repository, confined to the detached worktree `<worktree>` (never touch the
> main checkout or another worktree). Lane `<lane>`, plans in this order: `<plans>` (files in
> `documentation/issues/` of the worktree). For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking its recommended option. Re-locate moved code by the quoted snippets. If the plan
>    is wrong or impossible, or its "drop this plan if" condition holds, do not improvise: revert the working tree,
>    leave the plan file, note why, and go on to the next plan.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and both `strings.xml` files it names.
> 4. Verify: the unit tests of every module you touched (`./gradlew :<module>:desktopTest`; the full list is
>    `:chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`),
>    and a compile of every platform the change touches: desktop `:app:desktop:compileKotlin`; `androidMain` or
>    `app/android` → `:app:android:compileDebugKotlin`; `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    `wasmJsMain` → `:app:web:compileKotlinWasmJs`. Common code in `:presentation` or `:metronome` → all four. A
>    `.github` change → parse the YAML; a plist → `plutil -lint`. `DesktopSyncAuthenticatorTest` holds port 53682:
>    rerun it alone before calling the lane red; a corrupt incremental cache is retried with
>    `-Pkotlin.incremental=false`. Skip manual checks that need a device, an account, a store or a network
>    condition and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill and commit with a single `-m` sentence in the repo's voice (e.g. `Leave a bracket
>    unchanged unless it is a whole chord name.`) — one fix, one commit. Then run
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`, amending
>    at once if either prints. Never branch, merge, rebase, push, bump the version or dispatch a workflow.
> 7. Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
>    then the final test result.

## Merging

In the main checkout, in the order **E, A, P**, as lanes report: `git cherry-pick $START..<lane HEAD>`. A and P both
edit `CampfireViewModel.kt`, the root `CLAUDE.md` and `presentation/CLAUDE.md`: resolve keeping both sides' intent,
`CLAUDE.md` paragraphs and `strings.xml` word by word, never one side whole. Run the unit tests after each lane, and
after the last the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit, never by rewriting landed commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan; `git status` is clean apart from `TO_DO.md`;
  `git worktree list` shows only the checkout.
- Copy the README's "Manual steps owed" and "Manual checks owed" out first, then: if no plan file is left, delete the
  folder and commit `Remove the review plans.`; otherwise leave the skipped plans and ask.
- Report the commit count, skipped plans with reasons, the manual steps and checks owed (the Play Console declaration
  first), and that the website edit is uncommitted. Update the `nineteenth-review` memory. **Do not push.**
