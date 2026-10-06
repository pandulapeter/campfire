# Executing the sixteenth review

The brief for the orchestrating agent. Start it with *"Follow `documentation/issues/EXECUTION.md`."* Read
`README.md` in this folder first: the index, the lanes, the shared-file rules and the decisions are there, and the
user's answers to D1–D3 override whatever a plan file calls its default.

## 1. Preconditions — stop and tell the user if any fails

- `git status` shows nothing but `documentation/issues/` (and, if the user has still not committed it, their own
  `documentation/TO_DO.md` edit — leave that file alone, never stage it; every commit below stages named paths).
- HEAD is `8ee010b36` or a descendant that touches none of the files the plans name; otherwise re-locate each
  plan's quoted code before trusting it.
- The README's Decisions section records the answers: D1 plan 32 = A (the text as written), D2 plan 61 = B (one fixed
  line for every store), D3 plan 19 = dropped (already removed from the folder).
- The tests pass:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`,
  `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`, and the Node tests of `app/web`
  (`node --test tests/*.cjs` in `app/web`; see `app/web/CLAUDE.md`). `DesktopSyncAuthenticatorTest` holds port 53682:
  rerun it alone before calling anything red.

Then commit the plans (`git add documentation/issues && git commit -m "Add the sixteenth review plans."`, after
loading the `commit-messages` skill) and note `START=$(git rev-parse HEAD)`.

## 2. Lanes

One detached worktree per lane, next to the checkout: `git worktree add --detach ../Campfire-lane-<x> $START`.

| Lane | Worktree | Plans, in this order |
|---|---|---|
| A | `../Campfire-lane-a` | 01, 02, 03, 04, 05, 06 |
| B | `../Campfire-lane-b` | 10, 11, 12, 13, 14, 15, 16, 17, 23, 18, 20, 21, 22 |
| D | `../Campfire-lane-d` | 30, 48, 31, 33, 35, 34, 37, 36, 40, 41, 32, 45, 42, 43, 44, 46, 47, 38, 39 |
| E | `../Campfire-lane-e` | 60, 61, 62, 63, 64, 65 |

**At most two lanes building with Gradle at once.** Start A and B in one message; start D when A reports and E when
B reports (E needs Gradle only for plan 63's Android compile).

## 3. The subagent prompt (one `general-purpose` agent per lane; fill in the lane, its plans and its worktree)

> You are executing lane **<X>** of the sixteenth Campfire review. Work only inside the git worktree
> **<worktree path>** (a detached checkout of the commit that holds the plans); never touch the main checkout or
> another worktree. Read `documentation/issues/README.md` there (lanes, shared-file rules, Decisions — the recorded
> answers override a plan's own default), then carry out these plans **in this order**: **<plan numbers>**.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan (it may carry a `**Challenged:** amended` line — the file as it stands is the plan),
>    taking the recommended option or the README's recorded decision. Re-locate moved code by the quoted snippet. If
>    the plan is wrong, impossible, or its own "drop this plan if" condition holds, do not improvise another fix:
>    revert the working tree, leave the plan file, note why, move on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and the strings (both `values/strings.xml` and
>    `values-hu/strings.xml`) it names. In a shared file (root `CLAUDE.md`, `chordpro/CLAUDE.md`) change only the
>    sentence the plan quotes.
> 4. Verify: the module's tests (`./gradlew :<module>:desktopTest`; for `.github/scripts`,
>    `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`; for `app/web`, its Node tests) and a compile
>    of **every platform the change touches** — commonMain code is compiled for more than the desktop (a JVM-only
>    call in commonMain passed a desktop-only check in the last sweep and broke iOS and the web): desktop
>    `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation`), and for any commonMain,
>    `iosMain` or `wasmJsMain` change also `:app:web:compileKotlinWasmJs` and, at least once at the end of the
>    lane, `:app:ios:linkDebugFrameworkIosSimulatorArm64`; `androidMain` or `app/android` →
>    `:app:android:compileDebugKotlin`. One Gradle invocation at a time. A corrupt incremental cache is retried with
>    `-Pkotlin.incremental=false`; `DesktopSyncAuthenticatorTest` is rerun alone before it counts as a failure.
>    Manual checks that need a device, an account, a store or a network condition are skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill; commit with a single `-m` sentence — one fix, one commit. Then check:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"`; amend on the
>    spot if either prints. Never branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> Lane notes — A: 06 asks for one run of its test on `iosSimulatorArm64Test`; if the simulator target cannot run,
> use the plan's fallback table and say so. B: 15 before 17 (same lines, keep both changes); 23 edits
> `ChordProSplitter.kt` / `ChordProSyntax.kt` in `:chordpro` and one paragraph of `chordpro/CLAUDE.md` — nothing
> else there. D: 48 changes the shared sheet before 31, 33 and 35 edit their own sheets; in
> `settleSynchronizationBeforeExit` the order is 37's draft settle, `metronome.stop()`, 36's two writes, the sync
> wait. E: 60 before 61; 60 and 61 may edit `.claude/skills/prepare-release/SKILL.md`, only the bullets they quote.
>
> Final report: one line per plan — `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>` —
> then `Not done: NN — <why>`, then the result of the lane's last test run.

## 4. Merging

In the main checkout, in the order **A, B, E, D**, as lanes report: `git cherry-pick $START..<lane HEAD>`.
Conflicts are expected only in the root `CLAUDE.md` (10, 23, 32, 61, 62, 65) and `chordpro/CLAUDE.md` (01, 02, 04
against 23): resolve as a word-level three-way merge that keeps every sentence from both sides, never one side
whole. Run the unit tests (section 1) after each lane. After the last one, the full build:

`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`

A break is fixed by one more commit (`Fix the iOS build after the …`), never by rewriting landed commits. Then
`git worktree remove ../Campfire-lane-<x>` for each lane.

## 5. Finish

- Plan files still present are the skipped ones: leave them, trim the README to them, and ask the user.
- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan (plus any build fix); `git status` is clean apart from the
  user's `TO_DO.md` if it is still pending; `git worktree list` shows only the checkout.
- Report the commit count, the skipped plans with reasons, and the manual checks owed — copy them out of the README
  first — and update the memory note `sixteenth-review.md` with the commit range, what was skipped and those checks.
- When only `README.md` and `EXECUTION.md` are left, delete the folder and commit it as `Remove the review plans.`
  **Do not push.**
