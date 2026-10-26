# Executing the 46th review plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It restates
section 4 of `.claude/skills/codebase-review/SKILL.md` for this sweep; read that section too.

**This file covers two sweeps, the 46th (below) and the 47th (the section after it), run as one execution.** Check both
sections' preconditions first, then commit every untracked or changed file in `documentation/issues/` (plans 01–09,
`README.md`, `EXECUTION.md`) in one commit, `Add the 46th and 47th review plans.` (or `Add the 47th review plans.`
where the 46th's are already committed), and only then note one `START=$(git rev-parse HEAD)` for all four lanes, so
every worktree holds every plan file. The two "commit the plans" steps below are that one commit.

## Preconditions — stop and tell the user if any fails

1. **The tree is clean apart from `documentation/issues/` and the user's own files.** At the challenge (`01e6ff0d5`) the
   user had uncommitted work in `documentation/TO_DO.md`, `documentation/store-listings/`, and both `strings.xml`; no
   lane touches those. Any uncommitted change in a file a lane owns (README's Lanes table — `SongDetailsScreen.kt` and
   `screens/songDetails/CLAUDE.md` above all) stops the run. Never stash, commit, or discard the user's changes — ask.
2. The challenge (SKILL.md section 1, step 7) has run on all four plans, and the README records it (done 2026-10-26:
   01 sound, 02–04 amended).
3. Decisions D1, D2, and D3 in the README are answered.
4. `./gradlew desktopTest` passes.

Then commit the plans if untracked (`Add the 46th review plans.`), and note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree |
|---|---|---|
| A | 01, 02, 03 | `../Campfire-lane-a` |
| B | 04 | `../Campfire-lane-b` |

`git worktree add --detach ../Campfire-lane-<x> $START` for each; spawn both subagents (`general-purpose`, most capable
model) in one message. Two lanes are within the two-concurrent-Gradle-builds cap.

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`, `<SCRATCH>`)

> You are lane `<LANE>` of the 46th Campfire review execution. Work only in `<WORKTREE>`: every command starts with
> `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go in `<SCRATCH>/lane-<LANE>/` only; never run a script
> you did not write. Never `git stash`, branch, merge, rebase, push, or bump the version.
>
> Carry out, in this order, the plans `<PLANS>` in `<WORKTREE>/documentation/issues/`. For each:
> 1. Read the plan and every file it names; read the nearest `CLAUDE.md`. Load the `code-style` skill before the first
>    edit.
> 2. Implement exactly the plan with the README's answer to its decision (D1 for 01, D2 for 03, D3 for 04). Re-locate
>    code by the quoted snippets, not line numbers. If the plan is wrong, impossible, or its own drop condition holds,
>    revert the tree, leave the plan file, note why, and go on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files it names.
> 4. Verify (below). A check needing a device or a store is skipped and named in the report only if a mistake would hurt.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`; commit with one `-m` sentence. Then:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
>    amend on the spot if either prints. `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`, then
> the final test result. For plan 03 under D2 = B, include the probe's numbers (with and without the warm-up) and
> whether B or A shipped. For plan 04, what the check printed against the committed profile.

## Verification per lane

- **Lane A**: `./gradlew spotlessCheck :presentation:desktopTest :app:desktop:compileKotlin`; plan 01: the flag check
  on the toolchain JBR and the `Campfire.cfg` greps after `:app:desktop:createDistributable` and
  `:app:desktop:createReleaseDistributable`; plan 02: also `:app:android:compileDebugKotlin
  :app:web:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64` and `:tools:screenshots:test`; plan 03:
  `:tools:screenshots:test` and one `runRelease` (`campfire.log` shows no warm-up failure); under D2 = B also the probe
  (never with another Gradle build running beside the render). `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling the lane red; a corrupt
  incremental cache is retried with `-Pkotlin.incremental=false`.
- **Lane B**: `./gradlew spotlessCheck`, the generator module's compile task, and `./gradlew :app:android:help` (the
  build script still configures with the configuration cache); the check's logic once against the committed profile
  (it must name Chords, Scales, Settings, the editor, and the bottom sheet); the walk on an emulator only if one is
  available, without committing the regenerated profile.

## Merging

In order **B, then A**. For each lane: `git -C <worktree> log --stat --format=%s $START..HEAD` and stop on any path
outside the lane's files (README's Lanes table); then in the main checkout `git cherry-pick $START..<lane HEAD>`.
Keep both sides' intent in any conflict (`CLAUDE.md` word by word). Run `./gradlew desktopTest` after each lane, and the
full build after the last:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan; `git status` is clean apart from the user's own files;
  `git worktree list` shows only the checkout.
- Plan 01 under D1 = A changed the desktop launch flags, and plan 03 adds a background warm-up: name a `runRelease`
  start of the packaged app as the one hand check, if a lane could not run it.
- Update the 46th review memory (`46th-review.md`) with the commit range and what was skipped.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`
  **Do not push.**

---

# Executing the 47th review plans

The same run continues with the 47th's plans (`05`–`09`, README's "47th review" section) once the 46th's lanes are
merged or alongside them as below. Section 4 of `.claude/skills/codebase-review/SKILL.md` applies as for the 46th.

## Preconditions — in addition to the 46th's

1. The README's 47th "Challenge" section records the challenge as run on plans 05–09. If it still says "Still to run",
   stop and tell the user.
2. The 47th's decisions are answered (they are: README, 2026-10-26).
3. The tree is clean apart from the user's own files. The user may have uncommitted edits in `documentation/TO_DO.md`,
   `documentation/store-listings/`, and both `strings.xml` (What's new text): lane P still edits `strings.xml`, from a
   clean commit in its worktree; at its merge, an uncommitted change in either `strings.xml` in the main checkout stops
   the run — ask the user to commit it, never stash, commit, or discard it yourself.

Commit the plans if untracked (with the 46th's, `Add the 46th and 47th review plans.`, or `Add the 47th review plans.`
if the 46th's are already committed), then `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree | Cut from |
|---|---|---|---|
| R | 08 | `../Campfire-lane-r` | `$START` |
| P | 05, 06, 07, 09 | `../Campfire-lane-p` | the main checkout's `HEAD` after the 46th's lane A merged (`P_BASE=$(git rev-parse HEAD)` then) |

Order of work, within the two-concurrent-Gradle-builds cap: the 46th's A and B first; start R when B finishes (or as
soon as a slot is free); start P only after the 46th's A has been merged into the main checkout.

`git worktree add --detach ../Campfire-lane-r $START`; later `git worktree add --detach ../Campfire-lane-p $P_BASE`.

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`, `<SCRATCH>`)

> You are lane `<LANE>` of the 47th Campfire review execution. Work only in `<WORKTREE>`: every command starts with
> `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go in `<SCRATCH>/lane-<LANE>/` only; never run a script
> you did not write. Never `git stash`, branch, merge, rebase, push, or bump the version.
>
> Carry out, in this order, the plans `<PLANS>` in `<WORKTREE>/documentation/issues/`. For each:
> 1. Read the plan and every file it names; read the nearest `CLAUDE.md`. Load the `code-style` skill before the first
>    edit.
> 2. Implement exactly the plan with its recorded decision (07: the compact card; 08: beat dots count; 09:
>    "Listanézet"). Re-locate code by the quoted snippets, not line numbers (the 46th's lane A has changed
>    `SongDetailsScreen.kt` before lane P starts). If the plan is wrong, impossible, or its own drop condition holds,
>    revert the tree, leave the plan file, note why, and go on (plan 06 says what to do if 05 was dropped).
> 3. Add the tests it asks for; update the `CLAUDE.md` files and strings it names (both `values/` and `values-hu/`).
> 4. Verify (below). A check needing a device or a store is skipped and named in the report only if a mistake would hurt.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`; commit with one `-m` sentence. Then:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
>    amend on the spot if either prints. `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`, then
> the final test result. For plan 08, which consumers of `isMark` you checked and whether `ChordSheetConverterTest`
> changed.

## Verification per lane

- **Lane R**: `./gradlew spotlessCheck :chordpro:desktopTest :presentation:desktopTest`.
- **Lane P**: `./gradlew spotlessCheck :presentation:desktopTest :app:desktop:compileKotlin`; plan 05 also
  `:app:web:compileKotlinWasmJs` (BrowserHistory) and `:tools:screenshots:test`; plans 06 and 07 `:tools:screenshots:test`
  (never with another Gradle build running beside a render); plan 07 also `:app:android:compileDebugKotlin
  :app:ios:linkDebugFrameworkIosSimulatorArm64`. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before
  calling the lane red; a corrupt incremental cache is retried with `-Pkotlin.incremental=false`.

## Merging

After the 46th's **B, then A**: the 47th's **R, then P**. For each lane: `git -C <worktree> log --stat --format=%s
<base>..HEAD` (`$START` for R, `$P_BASE` for P) and stop on any path outside the lane's files (README's 47th Lanes
table); then in the main checkout `git cherry-pick <base>..<lane HEAD>`. Keep both sides' intent in any conflict
(`CLAUDE.md` and `strings.xml` word by word). Run `./gradlew desktopTest` after each lane, and after the last the full
build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

- The 46th's finish checks, over both sweeps' commits: no attribution trailer in `git log --format=%B $START..HEAD`,
  one line per plan, `git status` clean apart from the user's own files, only the checkout in `git worktree list`.
- No hand check is owed for the 47th (nothing here risks data loss, a crash, or a store rejection).
- Update the 47th review memory (`47th-review.md`) with the commit range and what was skipped.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`
  **Do not push.**
