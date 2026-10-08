<!--
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
  distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

# Executing the twenty-second review's plans

Started with *"Follow `documentation/issues/EXECUTION.md`."* You are the orchestrator. Read `README.md` first; this file
restates the `codebase-review` skill's execution section for this sweep.

## Preconditions — stop and tell the user if any fails

1. Every decision in the README's "Decisions" table is answered (it is, as of 2026-10-08): plan 33 runs (option B), plan
   43 runs both phases, every other plan takes its recommended option.
2. The working tree is clean apart from `documentation/issues/` (the user's own `documentation/TO_DO.md` may be
   modified — leave it alone and never stage it).
3. The unit tests pass:
   `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`
4. Commit the plans if untracked: `git add documentation/issues && git commit -m "Add the twenty-second review plans."`,
   then `START=$(git rev-parse HEAD)`.

## Lanes

One detached worktree per lane: `git worktree add --detach ../Campfire-lane-<x> $START` for x in d, c, b, s, p. Copy
`local.properties` into a lane only if its manual check needs the Dropbox key. Spawn one `general-purpose` subagent per
lane in a single message, each confined to its worktree. **At most two Gradle builds at a time machine-wide** (more
hit Kotlin daemon OOMs on the 24 GB Mac): give every lane this wrapper and forbid calling `./gradlew` directly —

```bash
# gradle-slot.sh <worktree> <tasks…>: holds one of two machine-wide build slots while Gradle runs.
SLOTS=/tmp/campfire-gradle-slots; mkdir -p "$SLOTS"; DIR="$1"; shift
while true; do for i in 1 2; do if mkdir "$SLOTS/slot$i" 2>/dev/null; then
  trap 'rmdir "$SLOTS/slot'$i'"' EXIT; cd "$DIR" && ./gradlew --console=plain -q "$@"; exit $?; fi; done; sleep 5; done
```

| Lane | Plans in order |
|---|---|
| d | 20, 21, 23, 27, 26, 28, 22, 24, 25, 29, 30, 35, 34, 31, 32, 33 |
| c | 40, 41, 44, 42, 43, 48, 45, 46, 47 |
| b | 65, 67, 63, 61, 62, 64, 66, 70, 71 |
| s | 69, 68 |
| p | 09, 06, 02, 07, 04, 08, 10, 11, 05, 03, 01 (01 is many commits, one per step) |

Plan 60 runs after all lanes have merged, in the main checkout, by one subagent.

## The lane subagent's prompt (fill in `<worktree>`, `<lane>`, `<plans>`)

> You execute review plans in the Campfire repo, confined to the detached worktree `<worktree>`. Never touch any other
> checkout; never branch, merge, rebase, push, bump the version or dispatch a workflow. Your plans, in this order:
> `<plans>` (files in `<worktree>/documentation/issues/`). Read the README there for the cross-plan rules, especially
> the Koin default-value rule.
>
> Per plan: (1) read the plan and the files it names; load the `code-style` skill before the first edit. (2) Implement
> exactly the plan, taking the recommended option or the one the README's Decisions table records; re-locate moved code
> by the quoted snippet or declaration name — this sweep split many files after the plans were written, so paths may
> differ. If the plan is wrong, impossible, or its own "drop this plan if" condition holds, do not improvise: revert,
> leave the plan file, note why, move on. A multi-step plan lands one commit per step; a step that cannot land stops
> the plan there, the plan file staying with a note of the steps done. (3) Add the tests it asks for; update the
> `CLAUDE.md` files and strings it names (both `values/` and `values-hu/`). (4) Verify through
> `gradle-slot.sh <worktree> …`: the touched modules' `desktopTest`, and a compile of every platform the change touches
> — desktop `:<module>:compileKotlinDesktop` (`:app:desktop:compileKotlin` for `:presentation` and the apps),
> `androidMain` → `:app:android:compileDebugKotlin`, `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
> `wasmJsMain` → `:app:web:compileKotlinWasmJs`, a Koin change → `:app:di:compileKotlinDesktop`, the packaged desktop
> runtime → `:app:desktop:createReleaseDistributable`. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone
> before calling the lane red; retry a corrupt incremental cache with `-Pkotlin.incremental=false`. Skip manual checks
> that need a device, an account, a store or a network, and report them. (5) `git rm` the plan file in the commit of its
> fix (the last step's commit for a multi-step plan). (6) Load `commit-messages`; commit with one `-m` sentence, then
>
> ```
> test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
> git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"
> ```
>
> and amend on the spot if either prints. (7) Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual:
> <what, why>`, then `Not done: NN — <why>`, then the final test result.

Lane b's workflow plans (61, 62, 70) cannot be run from a worktree: verify them with `actionlint` if installed, the
Python `unittest` in `.github/scripts`, and by reading; their manual checks (dispatched runs, `submit` off) go to the
user.

## Merging

In the main checkout, in the order **d, c, b, s, p**, as lanes report: `git cherry-pick $START..<lane HEAD>`. Resolve
conflicts keeping both sides' intent — `CLAUDE.md` paragraphs, `strings.xml`, `settings.gradle.kts` and `tests.yml` as a
three-way merge with every sentence, key, include and step from both sides. After each lane run the unit tests above;
after the last, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit (`Fix the web build after the sync split.`), never by rewriting landed commits.
Then `git worktree remove ../Campfire-lane-<x>`.

Then plan 60, in the main checkout, by one subagent with the per-plan procedure above, one commit per step.

## Finish

Plan files still present are the skipped ones: trim the README to them, or ask the user whether to delete the folder.
Checks: `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0; `git log
--oneline $START..HEAD` is one line per plan step; `git status` is clean apart from the user's `TO_DO.md`; `git worktree
list` shows only the checkout. Report the commit count, the skipped plans with reasons and the manual checks owed
(copy them out of the plans and the README first), update the `twenty-second-review` memory note, and regenerate the
Baseline Profile reminder. When only `README.md` and `EXECUTION.md` are left, delete the folder and commit
`Remove the review plans.` **Do not push.**
