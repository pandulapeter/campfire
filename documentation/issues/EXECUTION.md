# Executing the twenty-fourth review

You are the orchestrator. The user starts you with *"Follow `documentation/issues/EXECUTION.md`."* Read
`README.md` here first (the lanes, merge order, shared-file rules and the decisions), and the `codebase-review`
skill's section 4, which this brief makes concrete. The plans were written at `b5c8ed3b5`.

## Preconditions (stop and tell the user if any fails)

1. `git status --short` shows nothing outside `documentation/issues/`.
2. Every plan carries the challenge's outcome (README → Challenge), and every decision in README → Decisions has an
   answer recorded. A plan the user declined (its "drop this plan if…" condition holds) is `git rm`'d in the plans
   commit, not executed.
3. The unit tests pass on the current `HEAD`:
   `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`
4. Commit the plans if they are untracked: `Add the twenty-fourth review plans.` (load `commit-messages` first). Then
   `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Base | Plans, in order |
|------|------|-----------------|
| T | `START` | 01, 03, 05, 02, 04, 06, 07, 08, 09, 10 |
| L | `START` | 20, 21, 22, 23, 24 |
| A | `START` | 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43 |
| P | main `HEAD` after T, L and A merged | 50, 51, 52, 53, 54, 55, 62, 58, 56, 57, 59, 60, 61, 11 |

**At most two concurrent Gradle builds.**
1. Start T and L together.
2. Start A when the first of them reports.
3. Start P only after T, L and A are merged into the main checkout.

Worktrees, detached, next to the checkout:
`git worktree add --detach ../Campfire-lane-<x> <base>` (`<x>` = `t`, `l`, `a`, `p`). `local.properties` is not in a
worktree, and no plan's checks need the Dropbox key, so don't copy it.

## Subagent prompt (one `general-purpose` agent per lane; fill in `<WORKTREE>`)

> You carry out lane `<X>` of the twenty-fourth Campfire review, in the detached worktree `<WORKTREE>` and nowhere else.
> Plans, in this order: `<PLAN LIST>` (files under `<WORKTREE>/documentation/issues/`).
>
> **Isolation rules:**
> - Every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`.
> - Scratch files go only in `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/1a365281-8e94-4c29-865b-aae6ae1f9a89/scratchpad/lane-<x>/`.
> - Never run a script you did not write.
> - Never `git stash`, branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> **Per plan:**
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking its recommended option. Re-locate moved code by the quoted snippets, since
>    earlier plans move lines.
>    - If the plan is wrong or impossible, or its "drop this plan if…" condition holds, do not improvise another fix.
>    - Instead, `git -C <WORKTREE> checkout -- .` (and remove new files), leave the plan file, note why, and go on.
> 3. Add the tests it asks for. Update the `CLAUDE.md` files and both `strings.xml` files it names.
> 4. Verify:
>    - the unit tests of every module you touched (`:<module>:desktopTest`);
>    - `./gradlew spotlessCheck`, and `spotlessApply` if it fails;
>    - `python3 .github/scripts/check_license_headers.py` for new files;
>    - a compile of every platform the change touches:
>      - desktop: `:<module>:compileKotlinDesktop`, or `:app:desktop:compileKotlin` for `:presentation`;
>      - `androidMain`: `:app:android:compileDebugKotlin`;
>      - `iosMain`: `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>      - `wasmJsMain` and `app/web`: `:app:web:compileKotlinWasmJs`, plus the Node tests in `app/web/tests` when
>        `index.html` or the service worker changed.
>
>    Known false alarms:
>    - `DesktopSyncAuthenticatorTest` holds port 53682; rerun it alone before calling the lane red.
>    - A corrupt incremental cache: retry with `-Pkotlin.incremental=false`.
>
>    Skip manual checks that need a device, a microphone, a screen reader or a network condition, and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`. Commit with a single `-m` sentence, one plan per commit. Then run:
>    ```
>    git -C <WORKTREE> show --stat HEAD
>    test "$(git -C <WORKTREE> cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git -C <WORKTREE> log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
>    ```
>    Amend on the spot if the commit touched files outside the plan, or either check prints.
>
> **Lane report:**
> - one line per plan: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`;
> - then `Not done: NN — <why>`;
> - then the final test result.

## Merging (in the main checkout, in the order T, L, A, then P)

For each lane, as it reports:
1. `git -C ../Campfire-lane-<x> log --stat --format=%s <base>..HEAD`: stop on any path outside the lane's files
   (README → Lanes).
2. `git cherry-pick <base>..<lane HEAD>`.
   - Resolve conflicts keeping both sides' intent:
     - `strings.xml` key by key;
     - `CLAUDE.md` paragraphs as a word-level three-way merge, every sentence from both sides kept;
     - `CampfireViewModel.kt` with both sides' hunks.
   - Never take one side's file whole.
3. Run the unit tests (precondition 3's command).
   - A break is fixed by one more commit (`Fix the <what> after the <lane> changes.`), never by rewriting landed
     commits.
4. `git worktree remove ../Campfire-lane-<x>`.

After T, L and A have merged, cut lane P from the new `HEAD` and run it. After P, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`,
plus `spotlessCheck` and the Node tests in `app/web/tests`.

## Finish

- **Plan files still present** are the skipped ones. Trim the README to them, or ask the user whether to delete the
  folder.
- **Final checks:**
  - `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  - `git log --oneline START..HEAD` is one line per plan (plus any fix commits);
  - `git status` is clean;
  - `git worktree list` shows only the checkout.
- **Report:**
  - the commit count;
  - the skipped plans, with reasons;
  - the manual checks owed: copy them from README → Manual checks owed before the folder goes.
- **Memory:** update `twenty-fourth-review.md` with the commit range, what was skipped and why, and the manual checks
  owed.
- **Last step:** if only `README.md` and `EXECUTION.md` are left, delete `documentation/issues/` and commit
  `Remove the review plans.` **Do not push.**
