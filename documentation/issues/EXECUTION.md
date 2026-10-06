# Executing the seventeenth review

The brief for the orchestrating agent. Start it with *"Follow `documentation/issues/EXECUTION.md`."* Read
`README.md` in this folder first: the index, the lanes, the order constraints and the decisions are there, and the
user's answers to D1–D3 override whatever a plan file calls its default.

## 1. Preconditions — stop and tell the user if any fails

- `git status` shows nothing but `documentation/issues/` — or, if the user has still not committed them, their own
  `documentation/TO_DO.md`, `documentation/plans/` and `MetronomeControls.kt` changes: leave those alone and never
  stage them (every commit below stages named paths). If `MetronomeControls.kt` is still uncommitted, no lane may
  touch it (none of the plans does).
- HEAD is `1c52e5347` or a descendant that touches none of the files the plans name; otherwise re-locate each plan's
  quoted code before trusting it.
- The README's Decisions section records the user's answers to D1 (plan 13 kept), D2 (plan 22: only songbooks that
  change tempo or time) and D3 (plan 24: docs follow the code's 416dp).
- The tests pass:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`.
  `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before calling anything red.

Then commit the plans (`git add documentation/issues && git commit -m "Add the seventeenth review plans."`, after
loading the `commit-messages` skill) and note `START=$(git rev-parse HEAD)`.

## 2. Lanes

One detached worktree per lane, next to the checkout: `git worktree add --detach ../Campfire-lane-<x> $START`.

| Lane | Worktree | Plans, in this order |
|---|---|---|
| A | `../Campfire-lane-a` | 01, 02, 03, 04, 05 |
| B | `../Campfire-lane-b` | 10, 14, 15, 11, 12, 13 |
| D | `../Campfire-lane-d` | 20, 21, 22, 23, 24 |

**At most two lanes building with Gradle at once.** Start A and B in one message; start D when either reports.

## 3. The subagent prompt (one `general-purpose` agent per lane; fill in the lane, its plans and its worktree)

> You are executing lane **<X>** of the seventeenth Campfire review. Work only inside the git worktree
> **<worktree path>** (a detached checkout of the commit that holds the plans); never touch the main checkout or
> another worktree. Read `documentation/issues/README.md` there (lanes, order constraints, shared-file rules,
> Decisions — the recorded answers override a plan's own default), then carry out these plans **in this order**:
> **<plan numbers>**.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan (a `**Challenged:** amended` line means the file as it stands is the plan), taking
>    the recommended option or the README's recorded decision. Re-locate moved code by the quoted snippet. If the
>    plan is wrong, impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert
>    the working tree, leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and strings (both `values/strings.xml` and
>    `values-hu/strings.xml`) it names. In a shared file (root `CLAUDE.md`, `presentation/CLAUDE.md`) change only the
>    sentence the plan quotes.
> 4. Verify: the module's tests (`./gradlew :<module>:desktopTest`) and a compile of every platform the change
>    touches — desktop `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`), and for
>    any commonMain change also `:app:web:compileKotlinWasmJs` and, once at the end of the lane,
>    `:app:ios:linkDebugFrameworkIosSimulatorArm64`. One Gradle invocation at a time. A corrupt incremental cache is
>    retried with `-Pkotlin.incremental=false`. Manual checks that need a device, a real backup or a store are
>    skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill; commit with a single `-m` sentence — one fix, one commit. Then check:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`; amend on the
>    spot if either prints. Never branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Lane notes — A: 03 before 04 (04 also edits `ChordProParser.kt` and `ChordProSerializerTest.kt`); 04 assumes plan
> 21 (lane D) clamps the change's own tempo, which is that lane's business, not yours. B: 10 → 14 → 15 → 11 in
> `SongbookProBackup.kt`; 12 before 13; 13 keeps `planSetlists` / `replanSetlists` callable with a plain `Map` and
> appends `origin` last. D: 20 before 22; 21 clamps both the opening and the change's own tempo; do not touch
> `ui/metronome/MetronomeControls.kt`. After lane A has been merged into the main checkout, D's tests must also pass
> on top of it — the orchestrator checks that.
>
> Final report: one line per plan — `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>` —
> then `Not done: NN — <why>`, then the result of the lane's last test run.

## 4. Merging

In the main checkout, in the order **A, B, D**, as lanes report: `git cherry-pick $START..<lane HEAD>`. Conflicts are
expected only in the root `CLAUDE.md` (12 against 20, 22, 24) and `presentation/CLAUDE.md`: resolve as a word-level
three-way merge that keeps every sentence of both sides, never one side whole. Run the unit tests (section 1) after
each lane. After the last one, the full build:

`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

A break is fixed by one more commit, never by rewriting landed commits. Then `git worktree remove
../Campfire-lane-<x>` for each lane.

## 5. Finish

- Plan files still present are the skipped ones: leave them, trim the README to them, and ask the user.
- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan (plus any build fix); `git status` is clean apart from the
  user's own pending files; `git worktree list` shows only the checkout.
- Report the commit count, the skipped plans with reasons, and the manual checks owed — copy them out of the README
  first — and update the memory note `seventeenth-review.md` with the commit range, what was skipped and those checks.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit it as `Remove the review plans.`
  **Do not push.**
