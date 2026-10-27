# Executing the 48th review plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It restates
section 4 of `.claude/skills/codebase-review/SKILL.md` for this sweep; read that section too.

## Preconditions — stop and tell the user if any fails

1. **The tree is clean apart from `documentation/issues/`.** The user was editing
   `ui/screens/songDetails/autoScroll/AutoScrollControls.kt` during the review; any uncommitted change, in a lane's
   files above all (README's Lanes table), stops the run. Never stash, commit, or discard the user's changes — ask.
2. The challenge (SKILL.md section 1, step 7) has run on all twelve plans, and the README records it.
3. Every decision in the README's Decisions section has an answer.
4. `./gradlew desktopTest` passes.

Then commit the plans if untracked (`Add the 48th review plans.`), and note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree | Cut from |
|---|---|---|---|
| C | 01, 02 | `../Campfire-lane-c` | `$START` |
| S | 05 | `../Campfire-lane-s` | `$START` |
| I | 06 | `../Campfire-lane-i` | `$START` |
| A | 03, 04 | `../Campfire-lane-a` | `$START` |
| L | 07, 09, 10, 11, 08, 12 | `../Campfire-lane-l` | the main checkout's `HEAD` after lane A has merged |

`git worktree add --detach ../Campfire-lane-<x> <base>` for each. **At most two lanes build at once**: start C and A
first (the longest), then S and I as they finish; L only once A has merged (it shares `AppTest.kt` with A), its
cherry-pick range starting at its own base. Each subagent is `general-purpose`, on the most capable model.

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`, `<BASE>`)

> You are lane `<LANE>` of the 48th Campfire review execution. Work only in `<WORKTREE>`: every command starts with
> `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go in
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<LANE>/` only (the
> orchestrator fills in its own scratchpad); never run a script you did not write. Never `git stash`, branch, merge,
> rebase, push, bump the version, or dispatch a workflow. `local.properties` is not in the worktree and no plan needs
> it.
>
> Carry out, in this order, the plans `<PLANS>` in `<WORKTREE>/documentation/issues/`. For each:
> 1. Read the plan and every file it names; read the nearest `CLAUDE.md`. Load the `code-style` skill before the first
>    edit.
> 2. Implement exactly the plan, taking the README's answer to its decision (Decisions section) or else its recommended
>    option. Re-locate code by the quoted snippets, not line numbers. If the plan is wrong, impossible, or its own
>    "drop this plan if" condition holds, do not improvise another fix: revert the tree, leave the plan file, note why,
>    and go on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and strings it names (every `values*/strings.xml`: en, hu,
>    ro, de, es, fr). Growing a file already past its size limit is fine; pushing one past it is refused (code-style).
>    No test is ever `@Ignore`d: a new test failing on a real defect gets that defect fixed in its own commit first,
>    or the plan stops and is reported.
> 4. Verify (below). A check needing a device, an account, or a store is skipped and named in the report only if a
>    mistake would hurt.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`; commit with one `-m` sentence — one plan, one commit. Then:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
>    amend on the spot if either prints. `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`, then
> the final test result.

## Verification per lane

Every lane runs `./gradlew spotlessCheck` and the license header and size checks
(`python3 .github/scripts/check_license_headers.py`, `python3 .github/scripts/check_sizes.py`).

- **C**: `./gradlew :chordpro:desktopTest :data:formats:desktopTest :domain:implementation:desktopTest
  :presentation:desktopTest` (the rewriter feeds the editor and the viewer).
- **S**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :tools:screenshots:test --tests '*GoldenRenders*'`.
- **I**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :tools:screenshots:test --tests '*Tuner*'`.
- **A**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :tools:screenshots:test`.
- **L**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :tools:screenshots:test`, and for 07
  `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`, the new listing check on the four listings, and
  `python3 .claude/skills/add-language/scripts/language.py wiring <code> <Name>` for ro, de, es, and fr (each prints
  `OK`). The strings plans also `:app:web:compileKotlinWasmJs` (resources are generated per target).

`:tools:screenshots:test` renders offscreen: never run another Gradle task in the same worktree beside it.
`DesktopSyncAuthenticatorTest` holds port 53682 — rerun it alone before calling a lane red; a corrupt incremental cache
is retried with `-Pkotlin.incremental=false`. Golden images changed by a plan are re-recorded only where the plan says
so.

## Merging

In order **C, S, I, A, L**, as lanes report. For each: `git -C <worktree> log --stat --format=%s <base>..HEAD` and stop
on any path outside the lane's files (README's Lanes table); then in the main checkout
`git cherry-pick <base>..<lane HEAD>`. Keep both sides' intent in any conflict (`CLAUDE.md` and `strings.xml` word by
word, every sentence and key from both sides). Run `./gradlew desktopTest` after each lane; cut lane L once A is in.
After the last, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
(run the web distribution on its own if the combined build runs out of memory). A break is fixed by one more commit
(`Fix the iOS build after the tuner pill changes.`), never by rewriting landed commits. Then
`git worktree remove ../Campfire-lane-<x>`.

## Finish

Plan files still present are the skipped ones: leave them, the README trimmed to them, or ask the user. Final checks:
`git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0,
`git log --oneline $START..HEAD` is one line per plan (plus any build fix), `git status` is clean, and
`git worktree list` shows only the checkout. Report the commit count and the skipped plans with reasons. Update the
memory note `48th-review.md`. When only `README.md` and `EXECUTION.md` are left, delete the folder and commit
`Remove the review plans.` **Do not push.**
