<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 45th review plans

You are the orchestrating agent. The user started you with "Follow documentation/issues/EXECUTION.md." Read
`README.md` here first (lanes, merge order, shared-file rules, decisions). Load the `codebase-review` skill, section 4.
Spawn lane agents as `general-purpose` with `model: opus`. Do not push anything.

## Preconditions

Stop and tell the user if any fails:

1. `git status` is clean apart from `documentation/issues/` and the user's own edits to `documentation/TO_DO.md`,
   which no commit of this run stages (stage by explicit path, never `git add -A`).
2. The challenge ran on every plan (the README's **Challenge** section is no longer "Pending") and every decision in
   the README's **Decisions** is answered (06's read only line, 02's reverse case, 04's spiked drone).
3. `./gradlew desktopTest` passes. A failing `DesktopSyncAuthenticatorTest` (port 53682) is a known false alarm: rerun
   it alone.

Then commit the plans: load `commit-messages`, `git add documentation/issues && git commit -m "Add the 45th review plans."`,
and note `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane, next to the checkout: `git worktree add --detach ../Campfire-lane-<x> <base>`.

| Lane | Plans, in order | Base |
| --- | --- | --- |
| d | 05 | `START` |
| c | 04 | `START` |
| b | 02, 03 | `START` |
| a | 01, 06 | `START` |

At most two Gradle builds at once: start A (the longest) and D (merged first) in one message; start B and then C as
those finish. Every lane is cut from `START`, unless master moved since (the user commits while lanes run): then cut the
lanes not yet started from the main checkout's current `HEAD` and start their cherry-pick range at that base. No plan
relies on another lane's code; within lane A, 06 builds on 01, and within lane B, 03's doc sentence follows 02's.

## The subagent prompt

Fill in the worktree path at spawn time; everything else is filled per lane below the prompt.

```
You carry out lane <X> of the 45th review plans for Campfire, in the detached git worktree <absolute worktree path>
(base <base>). Lane plans, in this order: <plans>, each a file in documentation/issues/ of your worktree. Read
documentation/issues/README.md there first (shared-file rules, what each lane owns, decisions).

Keeping lanes apart: scratch files go only to
/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/f6a287ab-8fa8-4d9c-b3e7-81c4fba8d26b/scratchpad/lane-<x>/
and you run no script you did not write; every command starts with `cd <absolute worktree> &&` (or uses
`git -C <absolute worktree>`); after every commit run `git show --stat HEAD` and confirm it touched only that plan's
files (its **Files:** line); never `git stash`; never branch, merge, rebase, push, bump the version, or dispatch a
workflow. Run at most one Gradle build at a time.

Per plan, in order:
1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
2. Implement exactly the plan, taking the recommended option. Re-locate moved code by the quoted snippet. If the plan
   is wrong or impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert the
   working tree, leave the plan file, note why, and move on.
3. Add the tests it asks for and update the CLAUDE.md files it names (Oxford comma in English prose; a CLAUDE.md
   already past its word budget gets only what the plan says). No test is ever @Ignore'd: if a new test fails on a
   real defect, fix that defect in its own commit before the test's; one that cannot be fixed stops the plan and is
   reported. Growing a file already past its size limit is fine; pushing one past it is not.
4. Verify with: <verification>. Then `./gradlew spotlessCheck` (spotlessApply if it fails) and
   `python3 .github/scripts/check_doc_names.py <each CLAUDE.md you changed>`.
5. `git rm` the plan file in the same commit as its fix.
6. Load the `commit-messages` skill, then commit with a single -m sentence: one plan, one commit. Then run both checks
   and amend on the spot if either fires:
   test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
   git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"

Report, one line per plan: `NN: <hash> "<message>" - verified: <commands>; skipped manual: <what, why>`; then
`Not done: NN - <why>`; then the final test result. Name a skipped manual check only where a mistake would hurt.
```

Per lane:

| Lane | `<X>` / `<x>` | `<plans>` | `<base>` | `<verification>` |
| --- | --- | --- | --- | --- |
| D | D / d | 05 | `START` | `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs :tools:screenshots:compileKotlin` |
| C | C / c | 04 | `START` | `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs` |
| B | B / b | 02, 03 | `START` | `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs` |
| A | A / a | 01, 06 | `START` | `./gradlew :chordpro:desktopTest :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs` |

No plan touches `androidMain`, `iosMain`, `wasmJsMain`, or `desktopMain` sources (03 adds a `desktopTest`, which
`:presentation:desktopTest` runs), so the Android and iOS compiles wait for the full build after the last lane.

## Verification commands

- Unit tests: `./gradlew :presentation:desktopTest` (commonTest and desktopTest), `./gradlew :chordpro:desktopTest`
  (lane A, for the KDoc change; no `:chordpro` behaviour changes).
- Compiles: `:app:desktop:compileKotlin` for `:presentation`; `:app:web:compileKotlinWasmJs` for every lane (shared
  code reaches the web build); `:tools:screenshots:compileKotlin` for lane D (`Shot.kt` reads `SongGroups`).
- After the last merge: `./gradlew desktopTest :tools:screenshots:test` (the UI tests drive the song page and the export)
  and the full build:
  `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
- Known false alarms: `DesktopSyncAuthenticatorTest` (port 53682): rerun alone. On a corrupt incremental cache, rerun
  with `-Pkotlin.incremental=false`.
- At most two concurrent Gradle builds across all lanes and the main checkout.

## Merging

In the README's order (D, C, B, A); wait for D before merging anything, then merge each lane as it is ready in that
order:

1. List what each lane commit touched: `git -C ../Campfire-lane-<x> log --stat --format=%s <base>..HEAD`. Stop on any
   path outside the lane's files (the README's Lanes table and the plans' **Files:** lines).
2. In the main checkout: `git cherry-pick <base>..<lane HEAD>`. No file is shared between lanes in this sweep, so a
   conflict means a lane strayed: stop and look. Should one still need resolving, keep both sides' intent, `CLAUDE.md`
   paragraphs as a word-level three-way merge with every sentence from both sides.
3. Run `./gradlew :presentation:desktopTest` after each lane (and `:chordpro:desktopTest` after A). Then
   `git worktree remove ../Campfire-lane-<x>`.
4. After the last lane, the commands under "After the last merge" above. A break is fixed by one more commit (for
   example `Fix the web build after the capo changes.`), never by rewriting landed commits.

## Finish

- Final checks: `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'`
  prints 0; `git log --oneline START..HEAD` is one line per plan; `git status` is clean; `git worktree list` shows only
  the checkout.
- Update the memory file `45th-review.md` in
  `/Users/pandulapeter/.claude-personal/projects/-Users-pandulapeter-Projects-Campfire/memory/` (and its line in
  `MEMORY.md`): the commit range of the landed plans, and what was skipped and why.
- Report the commit count and the skipped plans with reasons; name a hand check only where a mistake would hurt (none
  is expected in this sweep: no data, sync, or store path changes).
- When only `README.md` and `EXECUTION.md` are left in `documentation/issues/`, delete the folder and commit it as
  `Remove the review plans.` without asking. If skipped plans remain, leave the folder, trimmed to them, until the user
  decides. Do not push.
