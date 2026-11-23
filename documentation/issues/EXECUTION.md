# Executing the 58th review's plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It is section 4 of
`.claude/skills/codebase-review/SKILL.md` made concrete; read that section first.

## Preconditions

Stop and tell the user if any fails:

- `git status` is clean apart from `documentation/issues/` (another session may be working in this checkout: wait for
  it, never stash or commit its files).
- Every plan carries a `**Challenged:**` line, or is listed as sound in `README.md`'s Challenge section.
- Every decision in `README.md` has an answer (they do: 02 (b), 07 (a)).
- `./gradlew desktopTest` passes.

Commit the plans if untracked (`Add the 58th review plans.`), then note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree |
|---|---|---|
| C | 01, 02, 03, 04, 09 | `../Campfire-lane-c` |
| W | 05, 08 | `../Campfire-lane-w` |
| I | 07 | `../Campfire-lane-i` |
| D | 06 | `../Campfire-lane-d` |

`git worktree add --detach ../Campfire-lane-<x> $START` for each. At most two lanes build with Gradle at once: start C
and W, then I and D as those finish. Lane C needs `local.properties` copied in for 01's and 03's corpus scores
(`campfire.importCorpus`, `campfire.pageCorpus`);
the others do not.

## The lane prompt

Fill in `<x>`, `<plans>`, and `<worktree>` (the absolute path):

> You execute review plans `<plans>` from `documentation/issues/` in the Campfire repo, in that order, confined to the
> detached worktree `<worktree>`. Every command starts with `cd <worktree> &&` or uses `git -C <worktree>`. Scratch files
> go to `/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/<session>/scratchpad/lane-<x>/` only; never run
> another lane's scripts. Never `git stash`, branch, merge, rebase, push, or bump a version.
>
> Per plan: read the plan and the files it names; load the `code-style` skill before the first edit. Implement exactly
> the plan, its recommended or decided option; re-locate code by the quoted snippets. If the plan is wrong, impossible,
> or its own "drop this plan if" holds, revert the tree, leave the plan file, note why, and go on. Add its tests; run a
> test that pins a defect once without the fix and see it fail (say so in your report only). Update the `CLAUDE.md`
> lines and strings it names. A file past its size limit may not end a commit longer than it began. Verify with the
> touched modules' tests and a compile of every platform touched: desktop `:<module>:compileKotlinDesktop`
> (`:app:desktop:compileKotlin` for `:presentation`), androidMain `:app:android:compileDebugKotlin`, iosMain
> `:app:ios:linkDebugFrameworkIosSimulatorArm64`, wasmJsMain `:app:web:compileKotlinWasmJs`. `git rm` the plan file in
> the same commit as its fix. Load `commit-messages`; commit with one `-m` sentence, then check:
> `test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"` and
> `git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"`,
> amending at once if either prints; `git show --stat HEAD` must list only that plan's files.
>
> Report: `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then `Not done: NN — <why>`,
> then the final test result.

Lane specifics to add to the prompt:

- **C**: 01 and 03 print `PageCorpusTest` and `ImportCorpusTest` scores before and after; report them. 09 merges with
  04's rewrite of `PageCropperImpl.songLinesOf`: the memo wraps the `PageSongLines.of(crop)` answer.
- **W**: 05 compiles only on Linux; locally check it with `cc -fsyntax-only` if WebKitGTK headers are missing (they
  are), else by reading, and name its manual check in the report. CI's `compile` job builds it.

## Merging

In the order **C, W, I, D**, as lanes report. For each: `git -C <worktree> log --stat --format=%s $START..HEAD`, stop
on any path outside the lane's files (`README.md`'s Lanes table); then in the main checkout
`git cherry-pick $START..<lane HEAD>`. Resolve conflicts keeping both sides: `data/source/remote/implementation/CLAUDE.md`
(W and I) as a word-level three-way merge, every sentence from both. Run `./gradlew desktopTest` after each lane. After
the last, the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:desktop:packageDistributionForCurrentOS
./gradlew :app:web:wasmJsBrowserDistribution
```

(the web link alone: the combined build runs out of memory), and `./gradlew :tools:screenshots:test`. A break is fixed
by one more commit, never by rewriting landed ones. `git worktree remove ../Campfire-lane-<x>` for each.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline $START..HEAD` is one line per plan; `git status` is clean; `git worktree list` shows only the
  checkout.
- Report the commit count and any skipped plans with reasons; name a hand check only where a mistake would hurt.
- Update the memory note `58th-review.md` with the landed range and what was skipped.
- If no plan file is left, delete `documentation/issues/` and commit `Remove the review plans.` without asking.
- **Do not push.**
