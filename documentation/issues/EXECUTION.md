# Executing the 51st review plans

The brief for the orchestrating agent, started with *"Follow `documentation/issues/EXECUTION.md`."* It restates
section 4 of `.claude/skills/codebase-review/SKILL.md` for this sweep; read that section too.

## Preconditions (stop and tell the user if any fails)

1. `git status` shows a clean tree apart from `documentation/issues/`.
2. The challenge (SKILL.md section 1, step 7) has run on every plan.
3. Decisions D1 and D2 in `README.md` have the user's answers. Each plan takes its recommended option unless the
   README records another answer; if the answer differs, edit the plan's Fix to match before starting its lane.
4. `./gradlew desktopTest` passes on `master`.

Then commit the plans (`git add documentation/issues && git commit -m "Add the 51st review plans."`, load
`commit-messages` first) and note `START=$(git rev-parse HEAD)`.

## Lanes

| Lane | Plans, in order | Worktree |
|---|---|---|
| T | 04 | `../Campfire-lane-t` |
| S | 03 | `../Campfire-lane-s` |
| P | 05 → 01 | `../Campfire-lane-p` |

The files each lane owns are in the README's Lanes table. Create every worktree detached from `START`:

```
git worktree add --detach ../Campfire-lane-t START
git worktree add --detach ../Campfire-lane-s START
git worktree add --detach ../Campfire-lane-p START
```

**At most two Gradle builds at once** (the 24 GB Mac's Kotlin daemons run out of memory beyond that): start lanes P
and T together, and S as soon as either reports. `local.properties` is not needed (no plan has a manual check that
needs the Dropbox key).

## The lane prompt

Spawn each lane as a `general-purpose` subagent (model: Opus), all available lanes in one message. Fill in `<x>`,
`<plans>`, and the worktree path:

> You execute the 51st review's plans `<plans>` (in this order) for Campfire, in the detached git worktree
> `<absolute worktree path>` and nowhere else. Read `.claude/skills/codebase-review/SKILL.md` section 4 ("Per plan")
> and follow it exactly; read the root `CLAUDE.md` and the nearest `CLAUDE.md` of every package you touch.
>
> Isolation rules:
> - Every command starts with `cd <absolute worktree path> &&` or uses `git -C <absolute worktree path>`.
> - Scratch files go to `<scratchpad>/lane-<x>/` only; never run a script you did not write. Nothing temporary is
>   committed (scratch tests and scripts are untracked and deleted before each commit).
> - Never `git stash`, branch, merge, rebase, push, bump the version, dispatch a workflow, or pass `--no-verify`
>   (the pre-commit hook runs the license, size, regex, doc-name, and Spotless checks; fix what it reports).
> - At most one Gradle build at a time in this lane.
>
> Per plan: read the plan file in `documentation/issues/` and every file it names; load the `code-style` skill before
> the first edit; implement exactly the plan's recommended option (re-locate code by the quoted snippets, since line
> numbers move); add its tests; update the `CLAUDE.md` files and strings it names. If the plan is wrong or impossible,
> or its "stop"/"drop" condition holds, do not improvise: revert the working tree, leave the plan file, note why, and
> go on. A defect that the plan's new test exposes is fixed in its own commit before the test's; never `@Ignore` a
> test. Verify with the lane's commands below, then `git rm` the plan file in the same commit as its fix. Load
> `commit-messages` and commit with one `-m` sentence, then run:
>
> ```
> test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
> git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
> git show --stat HEAD
> ```
>
> amending on the spot if either prints, and checking the stat shows only this plan's files.
>
> Verification for this lane: `<commands>`. `DesktopSyncAuthenticatorTest` holds port 53682: rerun it alone before
> calling the run red. A corrupt incremental cache is retried with `-Pkotlin.incremental=false`.
>
> Report: one line per plan, `NN: <hash> "<message>" — verified: <commands>; skipped manual: <what, why>`, then
> `Not done: NN — <why>`, then the final test result.

Lane commands (`<commands>`):

- **T:** `./gradlew :chordpro:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`. Plan 04 also
  runs its own scratch comparison of every chord's default before and after (described in the plan) and deletes it.
- **S:** `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`.
- **P:** `./gradlew :chordpro:desktopTest :presentation:desktopTest :data:source:local:implementation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`
  (the last test task holds `DocumentGoldenTest`, which runs real documents through the converter), and
  `python3 .github/scripts/check_regexes.py chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`
  after any regex change.

## Merging, in order T, S, P

As each lane reports (and only in this order), for lane `<x>` with base `START`:

1. `git -C ../Campfire-lane-<x> log --stat --format=%s START..HEAD`; stop if any commit touches a path outside the
   lane's files (README Lanes table).
2. In the main checkout: `git cherry-pick START..<lane HEAD>`. Resolve conflicts keeping both sides' intent;
   `CLAUDE.md` paragraphs and `strings.xml` as a word-level three-way merge, every sentence and key from both sides.
3. `./gradlew desktopTest`. A break is fixed by one more commit (`Fix the desktop tests after the random picks
   return change.`), never by rewriting landed commits.
4. `git worktree remove ../Campfire-lane-<x>`.

After the last lane, the full build:

```
./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS
```

## Finish

- `git log --format=%B START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0.
- `git log --oneline START..HEAD` shows one line per plan (plus any defect fix a plan's test exposed, and any build
  fix after a merge, each named in the report).
- `git status` is clean; `git worktree list` shows only the checkout.
- Report the commit count, the skipped plans with reasons, and a hand check only where a mistake would hurt.
- Update the memory `51st-review.md` (SKILL.md section 3): the landed commit range, what was skipped and why.
- When only `README.md` and `EXECUTION.md` are left, delete `documentation/issues/` and commit
  `Remove the review plans.` without asking. If skipped plans remain, the folder stays until the user decides.
- **Do not push.**

## Campfire specifics

- Plans folder `documentation/issues/`; branch `master`.
- Unit tests: `./gradlew desktopTest` (every module's `commonTest` on the desktop target); UI tests:
  `./gradlew :tools:screenshots:test`.
- Per-platform compiles: desktop `:app:desktop:compileKotlin` (`:presentation` and the apps), `:<module>:compileKotlinDesktop`
  for others; `androidMain` → `:app:android:compileDebugKotlin`; `iosMain` → `:app:ios:linkDebugFrameworkIosSimulatorArm64`;
  `wasmJsMain` → `:app:web:compileKotlinWasmJs`. No plan here touches a platform source set; the full build above
  covers Android and iOS once.
- Size budgets and string rules are the `code-style` skill's (`check_sizes.py`, in the pre-commit hook): growing a file
  already past its limit is fine, pushing one past it is not.
- Regexes: Android's ICU refuses a brace only the JVM accepts (`check_regexes.py`); plans 05 and 01 change regexes.
- New strings (plan 01) go into all eight `values*/strings.xml` in the same commit; plan 01's PDF round-trip test reads
  them from the files, so a missing or mistranslated marker fails it.
- Commit message examples in this repo's voice: `Read back the PDF's key and capo in every language the app prints
  them in.` · `Prefer chord shapes that keep the root.`
- A rename or move of classes leaves the Android Baseline Profile stale; prepare-release regenerates it.
