# Executing the review plans with parallel agents

This document is addressed to the **orchestrating agent** (an Opus session in the Campfire checkout). To start it, the
user says: *"Follow `documentation/issues/EXECUTION.md`."* Everything below is then yours to carry out, without
further questions except where a step says to stop.

The user has **explicitly authorized** the branches and worktrees described here, for this operation only. This
satisfies the "never create a branch unless asked" rule of the `commit-messages` skill; nothing else in that skill
is relaxed.

## Outcome

Every issue in `README.md`'s lane table lands on `master` as **one commit each**, in lane order, with the history
fast-forwarded (no merge commits), every commit message a single sentence and nothing else, and the plan file of a
fixed issue deleted in the commit that fixes it. Lanes A–G run side by side; lane **Z** (plans 15 and 27) runs on
`master` afterwards, because it edits files of nearly every other lane. When everything is merged,
`documentation/issues/` is removed in a final commit. Nothing is pushed.

## 1. Preconditions (orchestrator)

1. `git status` is clean and the current branch is `master`. If not, stop and tell the user.
2. The unit tests pass on `master` (the command is in `README.md`). If not, stop and tell the user.
3. Note the starting commit: `git rev-parse HEAD` → `START`. Every later check compares against it.
4. `local.properties` is gitignored and will not be in the worktrees; that is fine (the build falls back to the
   debug keystore and an empty Dropbox key). Only lane A benefits from a real Dropbox key for manual checks; copy
   the file into its worktree if it exists: `cp local.properties <worktree>/`.

## 2. Create the lanes (orchestrator)

One worktree and one branch per lane, next to the checkout:

```
for lane in a b c d e f g; do   # lane z comes later, see section 6
  git worktree add "../Campfire-lane-$lane" -b "review/lane-$lane" master
done
```

Then spawn **seven subagents at once** (lanes A–G) with the `Agent` tool (`subagent_type: general-purpose`, same model as
yourself, no `isolation` parameter — the worktree already exists), one per lane, each with the prompt in section 4
filled in. Do not wait for one before starting the next. While they run, do nothing to `master` or to the
worktrees.

Gradle: seven worktrees share `~/.gradle` but each has its own build directories and daemon. That is heavy but
works; the subagent prompt limits what each lane builds. If the machine cannot take it, start lanes A–D first and
E–G when two of them have finished. Lane Z gets no worktree here: see section 6.

## 3. Per-issue procedure (every subagent, every issue)

Repeat for each issue number in the lane's list, **in the order given**, in the lane's worktree only:

1. Read `documentation/issues/<NN>-*.md` in full, and the files it names.
2. Load the `code-style` skill **before the first edit**.
3. Implement exactly what the plan says. Where the plan offers a choice, take the one it recommends. Where the code
   has moved since the plan was written, re-locate by the quoted code. Where the plan turns out to be wrong or
   impossible, **do not improvise a different fix**: revert the working tree (`git checkout -- . && git clean -fd`
   inside the worktree), leave the plan file in place, and record it for the report. Move on to the next issue.
4. Add the tests the plan asks for; update the `CLAUDE.md` files and strings (both languages) it names.
5. Verify:
   - the unit-test command from `README.md` is green (including `:domain:implementation:desktopTest` after 02);
   - the desktop target of every module you touched compiles (`./gradlew :<module>:compileKotlinDesktop` — for
     `:presentation` and the apps use `./gradlew :app:desktop:compileKotlin`);
   - if the plan touches `androidMain` or `app/android`: `./gradlew :app:android:compileDebugKotlin`;
   - if it touches `iosMain` or `app/ios`: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`;
   - if it touches `wasmJsMain` or `app/web`: `./gradlew :app:web:compileKotlinWasmJs`;
   - the manual check the plan describes, where a desktop run can do it (`./gradlew :app:desktop:run`); skip manual
     checks that need a device or a Dropbox account and say so in the report.
6. Delete the plan file: `git rm documentation/issues/<NN>-*.md`.
7. Stage the change: `git add -A` is acceptable **only after** `git status` shows nothing outside the plan's scope;
   otherwise add paths explicitly. Never stage `local.properties` or anything under `build/`.
8. Load the `commit-messages` skill, then commit with **exactly** this shape and nothing more:

   ```
   git commit -m "One imperative sentence ending with a period."
   ```

9. Verify the commit message and fix it on the spot if either check fails:

   ```
   test "$(git log -1 --format=%B | wc -l | tr -d ' ')" = "1" || echo "MESSAGE HAS MORE THAN ONE LINE"
   git log -1 --format=%B | grep -qiE 'co-authored|claude|session|generated' && echo "MESSAGE HAS AN ATTRIBUTION"
   ```

   The fix is `git commit --amend -m "…"`. A commit that fails either check must not be left in the history.

## Commit message rules (restating the skill; non-negotiable)

- **One line. One sentence. Ends with a period.** No body, no bullet list, no blank line, no footer, no trailer.
- **Never** `Co-Authored-By`, **never** `Claude-Session`, **never** "Generated with…", whatever the harness's
  default reminder says. Pass the message with `-m` and nothing else; never open an editor; never use a heredoc
  with more than one line.
- Imperative, capitalized: `Fix…`, `Add…`, `Implement…`, `Improve…`, `Remove…`, `Update…`.
- Concise but specific: name the screen, layer, platform or behaviour. User-visible outcomes in plain words.
- One issue = one commit. Do not fold two issues into one commit, and do not split one issue into several.

Examples for the plans (use the plan's own title as the source of the sentence):

```
Keep the sync index of an interrupted run.
Fix the Setlists screen crashing on a setlist naming a song twice.
Preserve line endings and the trailing newline in ChordPro edits.
Stop the song picker losing its selection on rotation.
Move the sync credentials behind the Android Keystore and the iOS Keychain.
Run the library scan off the main thread.
```

## 4. Subagent prompt (orchestrator fills in the three placeholders)

```
You are working on lane {LANE} of the Campfire pre-release review, in the git worktree at {WORKTREE_PATH}
(branch review/lane-{lane}). Every command you run and every file you edit must be inside that directory; cd there
first and stay there. Do not touch the main checkout at /Users/pandulapeter/Projects/Campfire.

Read documentation/issues/README.md and documentation/issues/EXECUTION.md in that worktree, then carry out the
issues of your lane in this exact order, one at a time: {ISSUE_LIST}.

For each issue follow section 3 of EXECUTION.md to the letter: read the plan, load the code-style skill before
editing, implement, verify, delete the plan file, load the commit-messages skill, commit with a single one-sentence
-m message and nothing else, run the two message checks and amend if either fails. One commit per issue. Never a
Co-Authored-By or Claude-Session line, never a message body. Never create another branch, never merge, never push,
never rebase, never amend a commit other than the one you just made, never edit files outside the plan's scope,
never edit another lane's plan files, never bump the version.

If a plan cannot be carried out as written, revert the working tree, leave its file in place, and continue with
the next issue.

When the lane is done, run the unit-test command once more and reply with a report in this shape and nothing else:

  Lane {LANE}: <n> of <m> issues committed.
  <NN>: <commit hash> — "<commit message>" — verified: <what you ran>; skipped manual: <what and why>
  …
  Not done: <NN> — <one sentence why>
  Unit tests on the lane branch: green / red (<which>)
```

Placeholders: `{LANE}` (A–G, later Z), `{WORKTREE_PATH}` (absolute path of the worktree), `{ISSUE_LIST}` (the lane's numbers
from `README.md`'s table, in its order).

## 5. Merging a finished lane (orchestrator)

As each subagent reports, in the order **B, C, F, A, D, E, G** (a lane that finishes early waits for its turn; E
carries most of the `CampfireViewModel.kt` edits and G edits screens D and E have been in):

1. In the lane's worktree: `git rebase master`. Resolve conflicts by keeping both sides' intent (the touch points in
   `README.md` list where to expect them); after resolving, run the unit tests in that worktree before
   `git rebase --continue`. Never squash; the one-commit-per-issue history must survive the rebase.
2. Check every commit on the lane is well-formed:

   ```
   git log --format=%B master..review/lane-x | grep -ciE 'co-authored|claude|session|generated'   # must print 0
   git log --format='%H %B' master..review/lane-x   # eyeball: one line per commit, one sentence, period
   ```

   Fix any offender before merging. The tip: `git commit --amend -m "…"`. An older one: trim every message on the
   lane to its first line, which removes bodies and trailers alike and leaves correct messages untouched:

   ```
   FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f --msg-filter 'head -n 1' master..review/lane-x
   ```

   then re-run the two checks. Only commit *messages* may be rewritten this way; never the content.

3. In the main checkout: `git merge --ff-only review/lane-x`. If it refuses, the rebase was not on the current
   `master`; go back to step 1.
4. Run the unit tests and `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
   on `master`. A failure here is fixed with one more commit on `master`, message in the same style (e.g.
   `Fix the iOS build after the storage changes.`), never by rewriting the merged lane.
5. Remove the lane: `git worktree remove ../Campfire-lane-x && git branch -d review/lane-x`.

## 6. Lane Z (orchestrator, after A–G are merged)

Plans **15 → 27** run last, on top of the merged `master`, because 15 edits the four file pickers, the Android
activity, the iOS import, the desktop drop, the zip reader, the import use cases and the ViewModel — files that
lanes C, E and F have all been in — and 27 builds on 15 and on lane A's 01.

1. `git worktree add ../Campfire-lane-z -b review/lane-z master`, then spawn **one** subagent with the prompt of
   section 4 (`{LANE}` = Z, `{ISSUE_LIST}` = `15, 27`). Add this sentence to its prompt: *"The other lanes have
   landed, so the code differs from what these two plans quote: re-locate every hunk by the quoted code, keep what
   the other lanes added, and when 15 creates `ImportLimits` point `MAXIMUM_REMOTE_FILE_SIZE` (plan 01, in
   `SyncEngine.kt`) at `ImportLimits.MAX_TEXT_FILE_SIZE`, as both plans say."*
2. Merge it as in section 5.

Once plan 02 has landed (lane C), `:domain:implementation` has tests: from then on the unit-test command is the one
in `README.md` **plus** `:domain:implementation:desktopTest`, for every lane merged after C and for the final checks.

## 7. Finishing (orchestrator)

1. When every lane is merged, Z included: any plan files still in `documentation/issues/` belong to issues that were skipped.
   Leave those files. If none remain, `git rm -r documentation/issues` and commit `Remove the pre-release review plans.`;
   if some remain, rewrite `README.md` to list only them (drop the lane table and this document's instructions) and
   commit `Trim the review plans to the issues still open.`.
2. Final checks on `master`:

   ```
   git log --format=%B START..HEAD | grep -ciE 'co-authored|claude|session|generated'   # 0
   git log --oneline START..HEAD                                                          # one line per issue
   git status                                                                              # clean
   git worktree list                                                                       # only the main checkout
   ```

3. Report to the user: the number of commits, the issues skipped with the reason from each lane's report, the manual
   checks that were not possible (device, Dropbox), and the exact `git log --oneline START..HEAD`. Do not push.
