# Executing the twenty-ninth review

The user starts this with *"Follow `documentation/issues/EXECUTION.md`."* You are the orchestrator. Load the
`codebase-review` skill and follow its section 4; this file fills it in for this sweep.

## Preconditions — stop and tell the user if any fails

- `git status` is clean apart from `documentation/issues/`.
- `README.md` records that the challenge ran, and every decision D1–D5 has an answer recorded there.
- The unit tests pass on `HEAD`:
  `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`
- If the plans are untracked, load `commit-messages` and commit them as `Add the twenty-ninth review plans.` Then
  `START=$(git rev-parse HEAD)`.
- Before handing a decision-dependent plan to a lane, apply the recorded answer: edit the plan so it carries only the
  chosen option (D1 → 01, D2 → 13, D3 → 37, D4 → 39, D5 → 45). A plan whose decision was "leave it" is deleted with
  `git rm` in the orchestrator's own commit `Drop the plans the user declined.`

## Lanes

| Lane | Plans, in order | Worktree | Base |
|---|---|---|---|
| A | 02, 03, 04 | `../Campfire-lane-a` | `START` |
| D | 30, 31 | `../Campfire-lane-d` | `START` |
| B | 10, 11, 12, 13, 14, 15, 16, 01 | `../Campfire-lane-b` | main checkout `HEAD` after A merged (01 edits `chordpro/CLAUDE.md` after A) |
| C | 20, 21, 22, 23, 24, 25 | `../Campfire-lane-c` | `START` |
| E | 35, 36, 37, 38, 39, 40, 41, 42 | `../Campfire-lane-e` | `START` |
| F | 45, 46, 47, 48, 49, 50, 51 | `../Campfire-lane-f` | `START` |

Create each with `git worktree add --detach ../Campfire-lane-<x> <base>`. **At most two lanes build at once** (the
24 GB Mac hits Kotlin daemon OOMs beyond that): start A and D, then C and E as they finish, B once A has merged, F last.

## Subagent prompt (one `general-purpose` agent per lane; fill in `<WORKTREE>`)

> You carry out Lane <X> of the twenty-ninth Campfire review in the detached worktree `<WORKTREE>`, plans <list> in
> that order, each described in `<WORKTREE>/documentation/issues/NN-*.md`. Read `<WORKTREE>/CLAUDE.md` first.
>
> Rules that keep the lanes apart: scratch files go to
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/` only, and you never
> run a script you did not write; every command starts with `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`; after every
> commit, `git -C <WORKTREE> show --stat HEAD` confirms it touched only that plan's files; never `git stash`; never
> branch, merge, rebase, push, bump the version, or dispatch a workflow.
>
> Per plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking the recommended option (the orchestrator has already removed the options the
>    user declined). Re-locate moved code by the quoted snippet. If the plan is wrong, impossible, or its "drop this plan
>    if" condition holds, do not improvise another fix: revert the working tree (`git -C <WORKTREE> checkout -- .`), leave
>    the plan file, note why, and move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and strings it names (strings in both `values/strings.xml`
>    and `values-hu/strings.xml`).
> 4. Verify: the module tests the plan touches (`./gradlew :<module>:desktopTest`; `DesktopSyncAuthenticatorTest` holds
>    port 53682, so rerun it alone before calling it red; a corrupt incremental cache is retried with
>    `-Pkotlin.incremental=false`), `./gradlew spotlessCheck` on the touched modules, and a compile of every platform the
>    change touches: desktop `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`);
>    `androidMain` → `:app:android:compileDebugKotlin`; `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    `wasmJsMain` → `:app:web:compileKotlinWasmJs`. A `commonMain` change in `:presentation` compiles desktop and web at
>    least. Manual checks that need a device, an account, or a network are skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill and commit with a single `-m` sentence — one fix, one commit. Then run
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
>    and amend at once if either prints.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>` per plan, then
> `Not done: NN — <why>`, then the final test result.

Lane-specific notes to add to the prompt:
- **A:** plan 02's new table test must pass on every entry; plan 04 rewords docs only.
- **B:** plans 10 and 11 both reshape `SongLyrics.kt` and `ChordCells.kt`, in that order; plan 01 comes last and adapts
  its call sites to what 10 and 11 left. Plan 16 says `instrumentChips` if plan 11 landed.
- **C:** plans 20, 21, and 22 build on each other in `ChordsScreen.kt`; keep that order.
- **E:** `CampfireViewModel.kt`, `ImportReportScreen.kt`, `ConflictResolutionDropdown.kt`, and `ImportController.kt` are
  edited by several plans of this lane; follow the order.
- **F:** plan 48 must download the Windows and Linux Skiko runtime jars from Maven Central to set each library's builds;
  plans 48 and 49 both extend `OpenSourceComponents.kt` and its test; plan 51 edits English strings only.

## Merging

In the order **A, D, B, C, E, F**, as lanes report. For each: list what every lane commit touched
(`git -C <worktree> log --stat --format=%s <base>..HEAD`) and stop on any path outside the lane's files (README's Lanes
table); then in the main checkout `git cherry-pick <base>..<lane HEAD>`. Resolve `CLAUDE.md` and `strings.xml` conflicts
as a word-level three-way merge, keeping every sentence and key from both sides. Run the unit tests after each lane.
After the last, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit (`Fix the web build after the credits changes.`), never by rewriting landed
commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline START..HEAD` is one line per plan (plus any fix commits); `git status` is clean; `git worktree list`
  shows only the checkout.
- Copy the plans' manual checks into the memory note `twenty-ninth-review.md` (with the commit range and anything
  skipped), since the next step deletes them.
- If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` Otherwise leave the
  skipped plans with the README trimmed to them and ask the user.
- Report the commit count, the skipped plans with reasons, and the manual checks owed. **Do not push.** The Android
  Baseline Profile is a manual check owed before the next release if any plan moved classes.
