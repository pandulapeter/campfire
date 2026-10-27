<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 49th review plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It restates
section 4 of `.claude/skills/codebase-review/SKILL.md` for this sweep; read that section too.

## Preconditions — stop and tell the user if any fails

1. **The tree is clean apart from `documentation/issues/`.** Never stash, commit, or discard the user's changes — ask.
2. The challenge (SKILL.md section 1, step 7) has run on all ten plans, and the README records it.
3. Every decision in the README's Decisions section has an answer.
4. `./gradlew desktopTest` passes.

Then commit the plans if untracked (`Add the 49th review plans.`), and note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree | Cut from |
|---|---|---|---|
| E | 01, 02 | `../Campfire-lane-e` | `$START` |
| C | 04 | `../Campfire-lane-c` | `$START` |
| N | 05, 06, 07, 08, 09, 10, 03 | `../Campfire-lane-n` | `$START` |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. **At most two Gradle builds at once, the main
checkout counting as one**: start N (the longest) and E first, then C once E reports. Each subagent is
`general-purpose`, on the most capable model.

## Subagent prompt (fill in `<WORKTREE>`, `<LANE>`, `<PLANS>`)

> You are lane `<LANE>` of the 49th Campfire review execution. Work only in `<WORKTREE>`: every command starts with
> `cd <WORKTREE> &&` or uses `git -C <WORKTREE>`. Scratch files go in `<the orchestrator's scratchpad>/lane-<LANE>/`
> only; never run a script you did not write. Never `git stash`, branch, merge, rebase, push, bump the version, or
> dispatch a workflow. `local.properties` is not in the worktree and no plan needs it.
>
> Carry out, in this order, the plans `<PLANS>` in `<WORKTREE>/documentation/issues/`. For each:
> 1. Read the plan and every file it names; read the nearest `CLAUDE.md`. Load the `code-style` skill before the first
>    edit.
> 2. Implement exactly the plan, taking the README's answer to its decision (Decisions section) or else its
>    recommended option. Re-locate code by the quoted snippets, not line numbers: each plan of lane N is written
>    against the code the plans before it leave. If the plan is wrong, impossible, or its own "drop this plan if"
>    condition holds, do not improvise another fix: revert the tree, leave the plan file, note why, and go on.
> 3. Add the tests it asks for; update the `CLAUDE.md` files and strings it names (every `values*/strings.xml`: en,
>    hu, ro, de, es, fr, it). Growing a file already past its size limit is fine; pushing one past it is refused
>    (code-style). No test is ever `@Ignore`d: a new test failing on a real defect gets that defect fixed in its own
>    commit first, or the plan stops and is reported.
> 4. Verify (below). A check needing a device, an account, or a store is skipped and named in the report only if a
>    mistake would hurt.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages`; commit with one `-m` sentence — one plan, one commit. Then:
>    `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
>    `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`;
>    amend on the spot if either prints. `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
> then the final test result.

## Verification per lane

Every lane runs `./gradlew spotlessCheck` and the license header and size checks
(`python3 .github/scripts/check_license_headers.py`, `python3 .github/scripts/check_sizes.py`).

- **E**: `./gradlew :abc:implementation:desktopTest :app:desktop:compileKotlin`, and for 02
  `:app:desktop:createReleaseDistributable` (then confirm the image holds `libquickjs` and the release jar no `jni/`
  entries, as the plan says) and `:app:desktop:run`-free checks only; the plan's Manual check (Mac App Store sandbox,
  Windows) is skipped and named in the report. 01 also compiles iOS and the web:
  `:app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:compileKotlinWasmJs`.
- **C**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin`.
- **N**: `./gradlew :chordpro:desktopTest :presentation:desktopTest :app:desktop:compileKotlin`, and after 06 and the
  last plan `:tools:screenshots:test`; the strings plan (09) also `:app:web:compileKotlinWasmJs`; anything touching a
  platform source set compiles that platform (root `CLAUDE.md`, codebase-review "Per-platform compile checks").

`:tools:screenshots:test` renders offscreen: never run another Gradle task in the same worktree beside it.
`DesktopSyncAuthenticatorTest` holds port 53682 — rerun it alone before calling a lane red; a corrupt incremental
cache is retried with `-Pkotlin.incremental=false`.

## Merging

In order **E, C, N**, as lanes report. For each: `git -C <worktree> log --stat --format=%s $START..HEAD` and stop on any
path outside the lane's files (README's Lanes table); then in the main checkout `git cherry-pick $START..<lane HEAD>`.
Keep both sides' intent in any conflict (`CLAUDE.md` word by word, every sentence from both sides;
`LoadedSongEditor.kt` if 03's optional stepper step touched it, keeping 04's Sheet music header too). Run
`./gradlew desktopTest` after each lane. After the last, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
(run the web distribution on its own if the combined build runs out of memory). A break is fixed by one more commit,
never by rewriting landed commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

Plan files still present are the skipped ones: leave them, the README trimmed to them, or ask the user. Final checks:
`git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0,
`git log --oneline $START..HEAD` is one line per plan (plus any build fix), `git status` is clean, and
`git worktree list` shows only the checkout. Report the commit count and the skipped plans with reasons. Update the
memory note `49th-review.md`. When only `README.md` and `EXECUTION.md` are left, delete the folder and commit
`Remove the review plans.` **Do not push.**
