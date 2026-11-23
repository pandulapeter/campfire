<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Executing the 59th review's plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues-performance/EXECUTION.md`."* It is
section 4 of `.claude/skills/codebase-review/SKILL.md` made concrete; read that section first. Plan 02 is not part of
it: the owner runs it on the Windows laptop once everything else has landed.

## Preconditions

Stop and tell the user if any fails:

- `git status` is clean apart from `documentation/issues-performance/` (another session may be working in this
  checkout: wait for it, never stash or commit its files).
- Every plan carries a `**Challenged:**` line.
- D1 in `README.md` is answered (not now: after lane L, report the pop's frame so the owner can decide).
- `./gradlew desktopTest` passes.

Commit the plans if untracked (`Add the 59th review plans.`), then note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree | Cut from |
|---|---|---|---|
| M | 01 | `../Campfire-lane-m` | `START` |
| S | 06, 04, 03, 05, 13 | `../Campfire-lane-s` | `HEAD` after M merged |
| L | 07, 08, 12, 11 | `../Campfire-lane-l` | `HEAD` after M merged |
| N | 10, 09, 14 | `../Campfire-lane-n` | `HEAD` after M merged |

`git worktree add --detach ../Campfire-lane-<x> <base>`. Run M alone, merge it, then S and L together (at most two
Gradle builds at once), and N as the first of them finishes. Each lane's cherry-pick range starts at its own base.

## The lane prompt

Fill in `<x>`, `<plans>`, `<worktree>` (absolute), and `<scratchpad>`:

> You execute review plans `<plans>` from `documentation/issues-performance/` in the Campfire repo, in that order,
> confined to the detached worktree `<worktree>`. Every command starts with `cd <worktree> &&` or uses
> `git -C <worktree>`. Scratch files go to `<scratchpad>/lane-<x>/` only; never run another lane's scripts. Never
> `git stash`, branch, merge, rebase, push, or bump a version.
>
> Per plan: read the plan and the files it names; load the `code-style` skill before the first edit. **Measure first**:
> run the plan's Measure scenario with `./gradlew :tools:screenshots:benchmark` (plan 01's transition suite) and keep
> the numbers. Implement exactly the plan, its recommended option; re-locate code by the quoted snippets. If the plan
> is wrong, impossible, or its own "drop this plan if" holds, revert the tree, leave the plan file, note why, and go on.
> A fix the benchmark shows gaining nothing on its scenario is reverted and reported, not committed. Add its tests; run
> a test that pins a defect once without the fix and see it fail (say so in your report only). Update the `CLAUDE.md`
> lines it names. A file or function past its size limit may not end a commit longer than it began. Verify with the
> touched modules' tests, `./gradlew :tools:screenshots:test` for anything in `:presentation` (it has a 20-minute
> timeout; a hang there is a composition that never settles — take a `jstack` of the test JVM, see the
> `AWT-EventQueue` thread), `spotlessCheck`, and a compile of every platform touched: `:app:desktop:compileKotlin`
> for `:presentation`, `:app:android:compileDebugKotlin`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
> `:app:web:compileKotlinWasmJs` for common code. Measure again. `git rm` the plan file in the same commit as its fix.
> Load `commit-messages`; commit with one `-m` sentence, then check:
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
> amending at once if either prints; `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — before → after (scenario, warm frame 1 / worst) — verified: <commands>`, then
> `Not done: NN — <why>`, then the final test result.

Lane specifics to add to the prompt:

- **M**: the benchmark is permanent owner tooling; nothing probe-only is committed. Report one full run's table: it is
  the baseline every other lane compares with.
- **S**: 03 and 05 share the holder parameter 03 adds to `SongDetailsPage`; 05 extends it. `SongDetailsPage`,
  `SongPages`, `SongLyrics`, and `SongDetailsScreen` are past the function limit and may not grow.
- **L**: 11's training-run change is in `app/desktop`; check it with `:app:desktop:createReleaseDistributable`.
- **N**: 14 starts with an A/B run kept out of the repo and is dropped by its own gate if the frames do not grow; 09
  and 14 share the recording helper 09 adds.

## Merging

M first, alone; then S, L, N, as lanes report. For each: `git -C <worktree> log --stat --format=%s <base>..HEAD`, stop
on any path outside the lane's files (`README.md`'s Lanes table); then in the main checkout
`git cherry-pick <base>..<lane HEAD>`. Resolve conflicts keeping both sides: `ui/components/CLAUDE.md` as a word-level
three-way merge, every sentence from both. Run `./gradlew desktopTest :tools:screenshots:test` after each lane, and the
benchmark's transition suite after S and N together (05 and 10 both move work onto the settle frame). After the last,
the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS
./gradlew :app:web:wasmJsBrowserDistribution
```

(the web link alone: the combined build runs out of memory). A break is fixed by one more commit, never by rewriting
landed ones. `git worktree remove ../Campfire-lane-<x>` for each.

## Finish

Final checks: `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'`
prints 0, one commit per plan, `git status` clean, `git worktree list` only the checkout. Report the commit count, each
plan's before → after, and the skipped plans with reasons. Update the memory (`59th-review.md`). Leave 02 and this
folder: the owner runs 02 on Windows next (`02-find-why-the-windows-release-drops-frames.md`, its results table filled
in on that machine). Once 02 is done too and no plan file is left, delete the folder and commit
`Remove the review plans.` **Do not push.**
