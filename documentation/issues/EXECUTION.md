# Executing the 35th review's plans

The orchestrating agent follows this brief; the procedure it restates is the `codebase-review` skill's section 4.

## Preconditions (stop and tell the user if any fails)

1. `git -C /Users/pandulapeter/Projects/Campfire status --short` shows nothing but `documentation/issues/`.
2. Every plan carries the challenge's verdict (the README's Challenge section is filled in; amended plans carry a
   `**Challenged:** amended — …` line).
3. The README's Decisions are answered: all four decided plans (09, 12, 13, 14) are executed. A plan the user later
   declines is skipped by its lane: delete its file in the plans commit below and strike it from the lane list.
4. The unit tests pass:
   `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`
5. Commit the plans: `git add documentation/issues && git commit -m "Add the 35th review plans."`, then
   `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Worktree | Plans in order |
|------|----------|----------------|
| C | `../Campfire-lane-c` | 01, 02, 03, 04, 05, 06, 07 |
| A | `../Campfire-lane-a` | 09 (if decided), 10, 11, 12 (if decided), 13 (if decided), 14 (if decided) |
| P | `../Campfire-lane-p` | 08 |

`git -C /Users/pandulapeter/Projects/Campfire worktree add --detach ../Campfire-lane-<x> $START` for each. **At most
two Gradle builds at once**: start C and A first; start P when one of them finishes (its one plan is small). No lane
builds on another's commits.

## The subagent prompt (one `general-purpose` agent per lane, Opus)

> You execute lane <X> of the review plans in the detached worktree `<absolute worktree path>` (branch-less; never
> create a branch, never push, never bump the version). The plans are in `<worktree>/documentation/issues/`: carry
> out, in this order, <plan files>. Scratch files go to
> `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/` only, and you run
> no script you did not write. Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`. Never
> `git stash`.
>
> Per plan: (1) read the plan and every file it names; load the `code-style` skill before the first edit. (2) Implement
> exactly the plan, taking the recommended option; re-locate moved code by the quoted snippet. If the plan is wrong,
> impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert the working tree,
> leave the plan file, note why, move on. (3) Add the tests it asks for; update the `CLAUDE.md` files and strings it
> names. A file already past its size limit may grow; pushing one past it is refused by the hook, so split as the
> plan says. (4) Verify: the tests of every module touched (`:<module>:desktopTest`), plus `:app:desktop:compileKotlin`
> for a `:presentation` or app change, `:app:android:compileDebugKotlin` for `androidMain` or the manifest,
> `:app:ios:linkDebugFrameworkIosSimulatorArm64` for `iosMain` or the share plist, `:app:web:compileKotlinWasmJs` for
> `wasmJsMain`. A check needing a device, an account, or a network is skipped and named in your report only if a
> mistake there would hurt. (5) `git rm` the plan file in the same commit as its fix. (6) Load the `commit-messages`
> skill; commit with a single `-m` sentence, one plan one commit; then run
> `test "$(git -C <worktree> cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"`
> and `git -C <worktree> log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`
> and amend on the spot if either prints. After every commit, `git -C <worktree> show --stat HEAD` must list only
> that plan's files.
>
> Lane rules: <lane C: never edit files outside `chordpro/`; the converter may grow past 500 lines but new
> vocabularies go into the new files the plans name> / <lane A: never edit `ChordSheetConverter.kt` or any file under
> `presentation/src/commonMain`; your one edit to `chordpro/.../convert/CLAUDE.md` is one bullet appended at the end>
> / <lane P: never edit files under `data/`, `chordpro/`, or `domain/implementation/.../importing/`>.
>
> Report: per plan `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN —
> <why>`, then the final test result.

## Merging, in the order C, A, P, as lanes report

1. `git -C <worktree> log --stat --format=%s $START..HEAD`; stop on any path outside the lane's files (README).
2. In the main checkout: `git cherry-pick $START..<lane HEAD>`. Conflicts in `CLAUDE.md` files are word-level
   three-way merges keeping every sentence of both sides (lane A's appended bullet in `convert/CLAUDE.md` after lane
   C's lines).
3. After each lane: the unit tests above. After the last: the full build —
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS`
   and, on its own, `./gradlew :app:web:wasmJsBrowserDistribution` (the combined build OOMs in the web link). A break
   is fixed by one more commit, never by rewriting landed ones.
4. `git worktree remove ../Campfire-lane-<x>`.

Known false alarms: `DesktopSyncAuthenticatorTest` holds port 53682, rerun it alone before calling a lane red; a
corrupt incremental cache is retried with `-Pkotlin.incremental=false`.

## Finish

Plan files still present are the skipped ones: leave them with the README trimmed to them, or ask the user whether
to delete the folder. `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'`
prints 0; `git log --oneline $START..HEAD` is one line per plan plus the plans commit; `git status` is clean;
`git worktree list` shows only the checkout. When no plan file is left, delete the folder and commit `Remove the
review plans.`. Update the memory note `35th-review` (`~/.claude-personal/projects/-Users-pandulapeter-Projects-Campfire/memory/`)
with the commit range and what was skipped. **Do not push.** Prepare-release regenerates the Baseline Profile.
