# Executing the PDF export polish plans

Brief for the orchestrating agent. Load the `codebase-review` skill and follow its section 4; this file fills in the
specifics. Plans and `README.md` are in `documentation/issues/`.

## Preconditions (stop and tell the user if any fails)

- `git status` is clean apart from `documentation/issues/`; branch `master`.
- README's Decisions D1–D4 each have an answer recorded; a plan whose decision went against it is deleted first.
- README's Challenge section says it ran.
- `./gradlew :presentation:desktopTest :data:source:local:implementation:desktopTest` passes.
- Commit the plans (`Add the PDF export review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

Detached worktrees: `git worktree add --detach ../Campfire-lane-<x> $START`. At most two Gradle builds at once: start
B and A together, C when the first of them finishes; D only after A, B and C are merged into the checkout
(`git worktree add --detach ../Campfire-lane-d HEAD` at that point).

| Lane | Plans in order |
|------|----------------|
| A | 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14, 15 |
| B | 16, 17, 18, 19, 20 |
| C | 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34 |
| D | 35 |

## Subagent prompt (one `general-purpose` agent per lane)

> You work only inside the git worktree `<WORKTREE>` (a detached checkout of Campfire). Carry out the plans
> `documentation/issues/<NN>-*.md` for lane <X> in exactly this order: <ORDER>. For each plan: read it and the files it
> names; load the `code-style` skill before your first edit; implement exactly the plan, taking the recommended
> option, re-locating moved code by the quoted snippets; add the tests it asks for and the strings (both
> `values/strings.xml` and `values-hu/strings.xml`) and `CLAUDE.md` changes it names. Stay inside the files your lane
> owns per `documentation/issues/README.md` ("Lanes" and the shared-file rules) — in a shared file touch only the
> function or call the README assigns you. If a plan is wrong, impossible, or its "drop this plan if" condition holds,
> do not improvise: revert the working tree, leave the plan file, note why, go on. Verify with
> `./gradlew :presentation:desktopTest` and `./gradlew :app:desktop:compileKotlin`; a plan that touches anything
> platform-specific or a suspend/threading change (12, 17, 19, 27, 28) also compiles
> `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs` and `:app:ios:linkDebugFrameworkIosSimulatorArm64`
> (retry a corrupt incremental cache with `-Pkotlin.incremental=false`). Manual checks are skipped and reported. Then
> `git rm` the plan file, load the `commit-messages` skill, and commit the fix and the removal together with a single
> `-m` sentence; check `git cat-file commit HEAD | sed '1,/^$/d' | wc -l` prints 1 and the message has no attribution,
> amending if not. One plan, one commit. Never branch, merge, rebase, push or bump the version. Final report, per plan:
> `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what>`, then `Not done: NN — <why>`, then the last
> test result.

## Merging

In the main checkout, in the order **B, A, C**: `git cherry-pick $START..<lane HEAD>`. Conflicts: in
`PrintRenderer.kt` keep both lanes' functions; in `PrintExportSheet.kt` keep lane C's structure and re-apply A's and
B's one-line call changes (`layoutPrintDocument` arguments, `pdf(snapshot, title, onPage)`); `strings.xml` and
`CLAUDE.md` word by word, every key and sentence from both sides. Run `./gradlew :presentation:desktopTest` after each
lane. Then lane D on the merged HEAD, cherry-picked the same way. Full build after D:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`.
A break is fixed by one more commit. `git worktree remove` each lane.

## Finish

As the skill's section 4: skipped plans stay with a trimmed README; otherwise delete the folder and commit
`Remove the review plans.`. One line per plan in `git log --oneline $START..HEAD`, no attribution anywhere, clean
status, only the main worktree. Report the manual checks owed (copy them from the README first) and update the memory
note `pdf-export-review.md`. Do not push.
