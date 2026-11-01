<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 52nd review plans

The orchestrator follows the `codebase-review` skill's section 4; this is that section filled in for this sweep.

## Preconditions

Stop and tell the user if any fails:

- `git status --short` shows nothing but `documentation/issues/`.
- The challenge has run on every plan (the README's Challenge section) and every decision is taken (Decisions).
- `./gradlew desktopTest` passes.

Commit the plans if untracked: `git add documentation/issues && git commit -m "Add the 52nd review plans."`, then
`START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane: `git worktree add --detach ../Campfire-lane-<x> $START`.

| Lane | Plans, in this order | Verification |
|---|---|---|
| R | 02a, 06, 11, 14, 07, 04, 05, 03, 09, 10, 12 | `:chordpro:desktopTest`, `:presentation:desktopTest`, `:data:source:local:implementation:desktopTest` (document goldens), `:app:desktop:compileKotlin` |
| F | 01, 13, 19 | `:data:formats:desktopTest`, `:data:source:local:implementation:desktopTest`, `:presentation:desktopTest --tests '*PdfRoundTripTest*'` |
| K | 02b | `:chordpro:desktopTest`, `:app:desktop:compileKotlin` |
| E | 18 | `:chordpro:desktopTest`, `:app:desktop:compileKotlin` |
| S | 15, 16 | `:presentation:desktopTest`, `:tools:screenshots:test`, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin` (16 touches `SavedStateStore`) |

At most two Gradle builds at once (24 GB Mac). Start R and F; start K, E, and S one at a time as earlier lanes finish.
Every lane's code is common, so a desktop compile covers it; the full build runs once after the last merge.

## Lane prompt

Spawn one `general-purpose` subagent per lane (Opus), with `<x>`, `<plans>`, and `<commands>` from the table and
`<worktree>` the absolute worktree path:

> You carry out lane `<x>` of the 52nd Campfire review in the worktree `<worktree>` (detached HEAD; never branch,
> merge, rebase, push, bump the version, or dispatch a workflow). Plans, in this order: `<plans>`, in
> `<worktree>/documentation/issues/`; read `README.md` there for the lane rules and decisions. Scratch files go only in
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/449906a6-ff62-4588-b277-97646910a279/scratchpad/lane-<x>/`,
> and you run no script you did not write. Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`.
> Never `git stash`.
>
> For each plan: read it and the files it names; load the `code-style` skill before the first edit. Implement exactly
> the plan, taking the recommended option (the README's Decisions say which were chosen); re-locate moved code by
> the quoted snippets. If the plan is wrong, impossible, or its "drop this plan if" condition holds, do not improvise:
> revert the working tree, leave the plan file, note why, and move on. Add its tests (each must fail without the fix;
> no `@Ignore` — a real defect a test exposes is fixed in its own commit first), and update the `CLAUDE.md` files and
> strings it names. Verify with `./gradlew <commands> -q` (rerun `DesktopSyncAuthenticatorTest` alone before calling a
> run red; retry a corrupt incremental cache with `-Pkotlin.incremental=false`). `git rm` the plan file in the same
> commit as its fix. Load the `commit-messages` skill and commit with a single `-m` sentence, one plan per commit; then
> run `git show --stat HEAD` (only the plan's files) and
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo ATTRIBUTION`,
> amending on the spot if either prints.
>
> Report: `NN: <hash> "<message>" — verified: <commands>`, then `Not done: NN — <why>`, then the final test result.

## Merging

In the order **F, K, E, S, R**, as lanes report. For each lane, first
`git -C ../Campfire-lane-<x> log --stat --format=%s $START..HEAD` and stop on any path outside the lane's files (the
README's Lanes table); then in the main checkout `git cherry-pick $START..<lane HEAD>`. Resolve conflicts keeping
both sides' intent (`CLAUDE.md` and `strings.xml` as a word-level three-way merge). Run `./gradlew desktopTest` after
each lane. After the last:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS
./gradlew :tools:screenshots:test
```

(run the web distribution alone if the combined build runs out of memory). A break is fixed by one more commit, never
by rewriting landed ones. Then `git worktree remove ../Campfire-lane-<x>` for every lane.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline $START..HEAD` is one line per plan (plus any build fix).
- `git status` is clean; `git worktree list` shows only the checkout.
- Plan files still present are the skipped ones: trim the README to them, or ask the user. When none is left, delete
  the folder and commit `Remove the review plans.` without asking.
- Update the memory note `52nd-review.md` with the commit range and what was skipped and why. Do not push.
