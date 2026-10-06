# Executing the eighteenth review

You are the orchestrating agent. The user starts you with *"Follow `documentation/issues/EXECUTION.md`."*. Read
`README.md` in this folder first: it holds the lanes, the decisions, the shared-file rules and the merge order.

## Preconditions

Stop and tell the user if any of these fails.

1. `git status` shows a clean tree apart from `documentation/issues/`. The user's own uncommitted edit to
   `documentation/TO_DO.md` (two new to-do lines, made during the review) also counts as clean. Leave it alone, keep
   lanes and commits off it, and never stage it.
2. Every plan in the README's index has been challenged. Plans 39 and 57 carry their challenge result in the README's
   "Challenge" section.
3. Every decision in the README is answered. All are answered as of 2026-10-06.
4. The unit tests pass on `master`:

   ```
   ./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest
   ```

If the plans are untracked, commit them first: load the `commit-messages` skill, then
`git add documentation/issues && git commit -m "Add the eighteenth review plans."`. Then record
`START=$(git rev-parse HEAD)`.

## Lanes

Each lane gets one **detached** worktree next to the checkout:
`git worktree add --detach ../Campfire-lane-<x> $START`.

| Lane | Worktree | Plans, in this order |
|------|----------|----------------------|
| A | `../Campfire-lane-a` | 01, 02, 06, 05, 04, 07, 03, 08 |
| CD | `../Campfire-lane-cd` | 10, 11, 12, 13, 14, 15, 16, 57, 30, 31, 34, 33, 32, 37, 36, 38, 35, 39 |
| B | `../Campfire-lane-b` | 20, 21, 22, 23, 24 |
| P | `../Campfire-lane-p` | 51, 50, 56, 52, 53, 54, 55 |

**At most two lanes build at once**: six concurrent Gradle builds hit Kotlin daemon OOMs on this 24 GB Mac.
1. Start **CD** (the longest, so the critical path) and **A** together, in one message.
2. When A reports, start **B**.
3. When B reports, start **P**.

No lane needs `local.properties`.

## Subagent prompt

Use one `general-purpose` agent per lane. Fill in `<LANE>`, `<WORKTREE>` and `<PLANS>`:

> You carry out lane `<LANE>` of the eighteenth Campfire review, confined to the worktree `<WORKTREE>` (a detached
> checkout of the main repository). Work only inside it, and never touch `/Users/pandulapeter/Projects/Campfire`
> itself. The plans are in `<WORKTREE>/documentation/issues/`; read `README.md` there first, then the root
> `CLAUDE.md` and the `CLAUDE.md` of every module you change. Carry out these plans, in this order: `<PLANS>`.
>
> For each plan:
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit.
> 2. Implement exactly the plan, taking the recommended option where it offers a choice. The README's "Decisions"
>    section overrides a plan where they differ. Code moves between plans, so find it by the quoted snippet, not by
>    line number.
>    - If the plan is wrong, impossible, or its own "drop this plan if" condition holds, **do not improvise another
>      fix**. Restore the working tree (`git checkout -- . && git clean -fd -- ':!documentation'`), leave the plan file
>      in place, note why, and move on.
> 3. Add the tests it asks for. Update the `CLAUDE.md` files and the strings it names; a string goes into both
>    `values/strings.xml` and `values-hu/strings.xml`.
> 4. Verify:
>    - Run the unit tests of every module you touched (`./gradlew :<module>:desktopTest`).
>    - Compile every platform the change touches:
>      - desktop: `:<module>:compileKotlinDesktop`, or `:app:desktop:compileKotlin` for `:presentation`;
>      - `androidMain` and the manifest: `:app:android:compileDebugKotlin` (for the manifest also
>        `:app:android:processDebugMainManifest`);
>      - `iosMain`: `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>      - `wasmJsMain`: `:app:web:compileKotlinWasmJs`.
>    - A change to common `:presentation` code compiles at least desktop and Android.
>    - Known false alarms: `DesktopSyncAuthenticatorTest` holds port 53682, so rerun it alone before calling the lane
>      red. Retry a corrupt incremental cache with `-Pkotlin.incremental=false`.
>    - Skip any manual check that needs a device, an account or a network, and report it.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill, then commit with a single `-m` sentence: one fix, one commit. Then check the raw
>    commit object, and amend on the spot if either check prints anything:
>
>    ```
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"
>    ```
>
> **Never** branch, merge, rebase, push, bump the version or dispatch a workflow.
>
> End with a lane report, one line per plan:
> `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`.
> Then `Not done: NN — <why>`, then the final unit-test result.

### Lane-specific notes to append to the prompt

- **A:**
  - 03 re-roots through the `keysShape` helper that 07 adds, and edits only `normalized`'s `rewriteDefinition` in
    `ChordProNotation.kt`.
  - 05 and 04 share the null-returning move helper; 05 introduces it and 04 reuses it.
  - 05 and 16-style changes to pinned tests are expected (README D-05).
- **CD:**
  - 11's cache must be the `@Volatile` copy-on-write map the plan describes; no plain `HashMap`.
  - 14 replaces `SongChord.isSpelledWithFlats` with `spelling`. 30, 31 and 34 then edit the same class and `map`
    body: keep every earlier plan's lines.
  - 57 is decision B (parentheses), with the plan's sub-defaults.
  - 39 is last. It carries its own "drop this plan if" condition (visual parity of drawn vs composed cells on all four
    platforms). Compile desktop, Android, iOS and wasm for it.
- **B:**
  - 20 adds a constructor argument to about 60 `SyncEngine(...)` constructions in `SyncEngineTest`, through a shared
    `NoSetlistComparison` fake.
  - 21 changes `SetlistLocalSource.loadSetlists()`'s return type; follow every caller and test it lists.
  - Run `:data:repository:implementation:desktopTest` and `:data:source:local:implementation:desktopTest` after each.
- **P:**
  - 51 before 50.
  - 50 and 56 both edit `SongEditorScreen.kt` and the same `presentation/CLAUDE.md` bullets.
  - Compile `:app:android:compileDebugKotlin` and `:app:ios:linkDebugFrameworkIosSimulatorArm64` for 50.

## Merging

Merge in the README's order, **A, B, CD, P**, as the lanes report. A lane that finishes early waits for the ones ahead
of it.

For each lane, in the main checkout:
1. `git cherry-pick $START..<lane HEAD>`.
2. Resolve conflicts keeping both sides' intent.
   - `CLAUDE.md` paragraphs and `strings.xml`: a word-level three-way merge, every sentence and key from both sides
     kept, never one side's version whole.
   - `ChordProNotation.kt` (A's 03 against CD's 15) and `SongChords.kt`: keep both edits.
3. Run the unit tests after each lane.
4. A break is fixed by one more commit (e.g. `Fix the iOS build after the chord diagram changes.`), never by rewriting
   landed commits.
5. `git worktree remove ../Campfire-lane-<x>`.

After the last lane, run the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS
```

## Finish

1. Plan files still present are the ones a lane skipped.
2. Run the final checks:
   - `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints `0`;
   - `git log --oneline $START..HEAD` is one line per plan, plus any build-fix commits;
   - `git status` is clean;
   - `git worktree list` shows only the checkout.
3. Copy the README's "Manual checks owed" out before anything deletes it.
4. Report to the user: the commit count, the skipped plans with their reasons, and the manual checks owed.
5. Update the memory note `eighteenth-review.md` with the commit range, the skipped plans and the manual checks owed.
6. Then the last step:
   - **No plan file left** (only `README.md` and `EXECUTION.md`): delete the folder and commit it as `Remove the
     review plans.` without asking.
   - **Skipped plans remain:** trim the README to them and ask the user whether to delete the folder.

**Do not push.**
