<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the tenth review's plans

The brief for the orchestrating agent. The user starts it with *"Follow `documentation/issues/EXECUTION.md`."*
`README.md` next to this file is the index: lanes, merge order, shared-file rules, decisions.

## 1. Preconditions — stop and tell the user if any fails

1. `git status` is clean apart from `documentation/issues/`, and the branch is `master`.
2. The challenge has run on every plan (the README's Challenge section says so).
3. Every decision in the README has an answer: **D1** (plan 07: drop the references or restore the check), **D2**
   (single-column stepping: keep, or write a plan), **D3** (plan 14: the sentence or the Safari listener). Carry the
   answers into the plans before starting: 07 and 14 say which option; if D2 is "page by screen", write plan 20 in
   lane A after 19 before starting lanes.
4. The unit tests pass on `master`:

   ```
   ./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest
   ```

Then, if the plans are untracked, load `commit-messages` and commit them as `Add the tenth review plans.` Note
`START=$(git rev-parse HEAD)`.

## 2. Lanes

Four detached worktrees next to the checkout (no branch is created):

```
git worktree add --detach ../Campfire-lane-c START
git worktree add --detach ../Campfire-lane-b START
git worktree add --detach ../Campfire-lane-d START
git worktree add --detach ../Campfire-lane-a START
```

| Lane | Plans, in this order |
|------|----------------------|
| C | 01, 02 |
| B | 03, 04, 10, 05 (06 was dropped by the challenge; `git rm` its file with the first lane B commit, or leave it for the finish) |
| D | 07, 08, 09 |
| A | 15, 16, 11, 17, 18, 19, 12, 13, 14 |

**At most two Gradle builds at once** (six lanes at once hit Kotlin daemon OOMs on this 24 GB Mac): start C and B
first, D when either finishes, A when the next finishes. Lane D needs no Gradle at all except the test run of plan 09's
Python test (`python3 -m unittest discover -s .github/scripts -p 'test_*.py'`) and one `desktopTest` to prove nothing
else moved; it can run beside two Kotlin lanes. `local.properties` is not in a worktree and no lane needs it.

Spawn one `general-purpose` subagent per lane, each confined to its worktree, with this prompt (fill in the lane
letter, the worktree path and the plan list):

> You are executing review plans for the Campfire repository in the worktree `<path>` (a detached worktree of
> `master`; never touch `/Users/pandulapeter/Projects/Campfire` itself). Work only inside that worktree. Never branch,
> merge, rebase, push, bump the version or dispatch a workflow. Carry out these plans from `documentation/issues/`, in
> this order: `<NN, NN, …>`. For each plan:
>
> 1. Read the plan and every file it names. Load the `code-style` skill before your first edit; it governs the
>    license header, KDoc versus `//`, trailing commas, `modifier` first, strings in both `strings.xml` files, and
>    keeping the module `CLAUDE.md` files in step.
> 2. Implement exactly what the plan says, taking its recommended option and the decision the README records for
>    it. Re-locate moved code by the quoted snippet, not by line number. If the plan is wrong, impossible, or its own
>    "drop this plan if" condition holds, do not improvise another fix: `git checkout -- .` (and remove untracked files
>    you added), leave the plan file in place, note why in your report, and move on.
> 3. Add the tests the plan asks for; update the `CLAUDE.md` files and strings it names.
> 4. Verify: the unit tests
>    `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :presentation:desktopTest`
>    and a compile of every platform the change touches: `:app:desktop:compileKotlin` for `:presentation` and
>    the apps' common code, `:app:android:compileDebugKotlin` for `androidMain`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`
>    for `iosMain`, `:app:web:compileKotlinWasmJs` for `wasmJsMain`. A change in `data/` or `domain/` is compiled with
>    `:app:desktop:compileKotlin` at least. `DesktopSyncAuthenticatorTest` holds port 53682 and flakes when two lanes
>    test at once — rerun it alone before calling the lane red; a corrupt incremental cache is retried with
>    `-Pkotlin.incremental=false`. Manual checks that need a device, an account, a store or a network condition are
>    skipped and reported.
> 5. `git rm` the plan file in the same commit as its fix.
> 6. Load the `commit-messages` skill; commit with a single `-m` sentence in the repo's voice — one plan, one commit,
>    never two folded or one split, and no attribution trailer of any kind. Then check the raw commit object and
>    amend on the spot if it fails:
>
>    ```
>    test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
>    git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "ATTRIBUTION"
>    ```
>
> Report, in this form: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>` per plan, then
> `Not done: NN — <why>` for any plan left, then the final result of the full test command.

## 3. Merging, in the README's order: C, B, D, A

As each lane reports, in the main checkout:

```
git cherry-pick START..<lane HEAD>
```

Resolve conflicts keeping both sides' intent. `CLAUDE.md` files and `strings.xml` are merged word by word as a
three-way merge — every sentence and every key from both sides kept, never one side's version whole;
`presentation/CLAUDE.md`'s paragraphs are one line each, so nearly every lane conflicts there and it is always this
kind of merge. Run the unit tests after each lane. After lane A, the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS
```

A break is fixed by one more commit (`Fix the iOS build after the song details changes.`), never by rewriting a landed
commit. Then `git worktree remove ../Campfire-lane-<x>` for each lane.

## 4. Finish

Plan files still present are the skipped ones: leave them, trim the README to them, and ask the user whether to
delete the folder. Final checks:

```
git log --format=%B START..HEAD | grep -ciE 'co-authored|claude|session|generated'   # prints 0
git log --oneline START..HEAD                                                        # one line per plan (+ fix-ups)
git status                                                                           # clean
git worktree list                                                                    # only the checkout
```

Copy the README's "Manual checks owed" out before the last step. When no plan file is left (only `README.md` and
`EXECUTION.md`), delete `documentation/issues/` and commit it as `Remove the review plans.` without asking. Update the
memory note `tenth-review.md` with the commit range, what was skipped and why, and the manual checks owed. **Do not
push.**
