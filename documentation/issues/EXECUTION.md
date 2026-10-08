<!--
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
  distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

# Executing the twenty-third review's plans

Started with *"Follow `documentation/issues/EXECUTION.md`."* You are the orchestrator. Read `README.md` first. This
file restates the execution section of the `codebase-review` skill for this sweep.

## Preconditions — stop and tell the user if any fails

1. The README's Decisions section has nothing open (true as of 2026-10-08), and the challenge has run on every plan.
2. The working tree is clean apart from `documentation/issues/`. The user's own `documentation/TO_DO.md` may be
   modified: leave it alone and never stage it.
3. The unit tests pass:
   `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest`
4. If the plans are untracked, commit them:
   `git add documentation/issues && git commit -m "Add the twenty-third review plans."`. Then `START=$(git rev-parse HEAD)`.

## Lanes

Create one detached worktree per lane with `git worktree add --detach ../Campfire-lane-<x> $START`, for x in d, c and e.
No lane needs `local.properties`. Spawn one `general-purpose` subagent per lane, all in a single message, each confined
to its worktree.

**At most two Gradle builds may run at a time machine-wide.** Lanes c and e build little: c runs only a shell script,
and e runs one compile. Still, give every lane this wrapper and forbid calling `./gradlew` directly:

```bash
# gradle-slot.sh <worktree> <tasks…>: holds one of two machine-wide build slots while Gradle runs.
SLOTS=/tmp/campfire-gradle-slots; mkdir -p "$SLOTS"; DIR="$1"; shift
while true; do for i in 1 2; do if mkdir "$SLOTS/slot$i" 2>/dev/null; then
  trap 'rmdir "$SLOTS/slot'$i'"' EXIT; cd "$DIR" && ./gradlew --console=plain -q "$@"; exit $?; fi; done; sleep 5; done
```

| Lane | Plans in order |
|---|---|
| d | 03, 02 |
| c | 01 |
| e | 04 |

## The lane subagent's prompt (fill in `<worktree>`, `<lane>`, `<plans>`)

> You execute review plans in the Campfire repo, confined to the detached worktree `<worktree>`. Never touch any other
> checkout. Never branch, merge, rebase, push, bump the version or dispatch a workflow. Your plans, in this order:
> `<plans>` (files in `<worktree>/documentation/issues/`). Read the README there first.
>
> For each plan:
>
> 1. Read the plan and the files it names. Load the `code-style` skill before the first edit.
> 2. Implement exactly the plan, taking the recommended option. Re-locate moved code by the quoted snippet. If the plan
>    is wrong, impossible, or its own "drop this plan if" condition holds, do not improvise another fix: revert the
>    working tree, leave the plan file, note why, and move on.
> 3. Add the tests it asks for, and update the `CLAUDE.md` files it names.
> 4. Verify through `gradle-slot.sh <worktree> …`: the touched modules' `desktopTest`, plus a compile of every platform
>    the change touches:
>    - a Koin or build change: `:app:desktop:compileKotlin` and `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
>    - a KDoc change: `:<module>:compileKotlinDesktop`;
>    - every lane: `python3 .github/scripts/check_license_headers.py`.
>
>    For known false alarms:
>    - `DesktopSyncAuthenticatorTest` holds port 53682, so rerun it alone before calling the lane red.
>    - Retry a corrupt incremental cache with `-Pkotlin.incremental=false`.
>
>    Lane c tests its script in the scratchpad with fake launchers, under `/bin/bash` (3.2), as the plan describes.
>    Skip manual checks that need a device, an account, a store or a network, and report them.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load `commit-messages` and commit with one `-m` sentence. Then run
>
>    ```
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"
>    ```
>
>    and amend on the spot if either prints anything.
> 7. Report `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then
>    `Not done: NN — <why>`, then the final test result.

## Merging

Merge in the main checkout in the order **d, c, e**, as the lanes report, with `git cherry-pick $START..<lane HEAD>`.
No file is shared between lanes. If a conflict appears anyway, keep both sides' intent.

Run the unit tests above after lane d. After the last lane, run the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
Fix a break with one more commit, never by rewriting landed commits. Then `git worktree remove ../Campfire-lane-<x>`.

## Finish

1. Any plan files still present are the skipped ones. Trim the README to them, or ask the user whether to delete the
   folder.
2. Check that:
   - `git log --format=%B $START..HEAD | grep -ciE 'co-authored|claude|session|generated'` prints 0;
   - `git log --oneline $START..HEAD` is one line per plan;
   - `git status` is clean apart from the user's `TO_DO.md`;
   - `git worktree list` shows only the checkout.
3. Report the commit count, the skipped plans with reasons, and the manual checks owed. Copy them out of the README
   first, since the next step deletes it.
4. Update the `twenty-third-review` memory note.
5. When only `README.md` and `EXECUTION.md` are left, delete the folder and commit `Remove the review plans.`
   **Do not push.**
