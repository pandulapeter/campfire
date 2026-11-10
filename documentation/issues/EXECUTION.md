<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Executing the 56th review's plans

You are the orchestrator. The user started you with *"Follow `documentation/issues/EXECUTION.md`."* Load the
`codebase-review` skill and follow its section 4; this file fills it in for this sweep. Do not push.

## Preconditions (stop and tell the user if any fails)

1. `git status` is clean apart from `documentation/issues/`.
2. The README's **Challenge** line says the challenge ran, and every plan it amended carries its `**Challenged:**` line.
3. Every item under the README's **Decisions** (D1–D4) has an answer recorded. If D1 (the
   public predicate in `:libs:abc:api`) was declined, plan 03 keeps the regex in `:presentation` (a copy of `TAB_VOICE`
   beside the spelling) instead. If D3 was declined, skip plan 20 (it needs rewriting around the owner's answer); if D4
   was declined, skip plan 21. D2 changes no plan.
4. `./gradlew desktopTest` passes.
5. Commit the plans if untracked (`Add the 56th review plans.`), then `START=$(git rev-parse HEAD)`.

## Lanes

Paths: `ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

| Lane | Worktree | Plans in order | Files (see the README's Lanes table for the full list) |
|------|----------|----------------|--------------------------------------------------------|
| N | `../Campfire-lane-n` | 12, 03, 05, 20, 21, 14, 02, 01 | `ui/notation/SongNotations.kt`, `NotationPlacement.kt`, `NotationChordNames.kt`, `AbcTitles.kt`, `NotationCache.kt` (`keyOf`, placed widths), `:libs:abc` api and `AbcRendererImpl.kt`, `ui/songView/SongNotationBlock.kt`, `SongSectionContent.kt`, `SongLyrics.kt`, `SongSectionsLayout.kt`, `model/SongUnits.kt`, `model/UnitContents.kt`, `grid/SectionMeasurements.kt`, `grid/StripPlacement.kt`, `tools/screenshots` `SheetMusicColumnTest.kt` and its song |
| D | `../Campfire-lane-d` | 06, 07, 13 | `ui/notation/NotationPainter.kt`, `DrawNotationSystem.kt`, `NotationCache.kt` (`notationWordsOf`), `ui/print/PrintRenderer.kt` |
| T | `../Campfire-lane-t` | 04, 09, 10 | `ui/songView/TabStaffLines.kt`, `ui/songLayout/LineRuns.kt`, `libs/chordpro/…/tabs/ChordProTabWrapper.kt` |
| P | `../Campfire-lane-p` | 08 | `ui/screens/export/LaidOutDocumentProducer.kt`, `ui/print/PrintNotations.kt`, `ui/print/PrintEngravings.kt` |
| G | `../Campfire-lane-g` | 11 | `ui/songView/FadingIn.kt`, `ui/songView/SongSectionChildren.kt`; the `fadingIn` call sites plan 11 names in `SongSectionContent.kt`, `SongScalesSection.kt`, `SongChordsSection.kt` |

All lanes are cut from `START` (`git worktree add --detach ../Campfire-lane-<x> $START`). **At most two Gradle builds
at once**: start N and T together (in one message); as each finishes, start the next of D, P, G.

## The lane prompt

Spawn one `general-purpose` subagent per lane on the most capable model, with this prompt (fill `<x>`, `<plans>`, and
`<commands>`; the worktree path is `<worktree>`):

> You carry out lane `<x>` of the 56th Campfire review, in the detached git worktree `<worktree>` and nowhere else.
> Plans, in this order: `<plans>` (files in `<worktree>/documentation/issues/`; read `README.md` there for the lane
> rules and shared files). For each plan, follow the `codebase-review` skill's section 4, "Per plan": read the plan and
> every file it names and the `CLAUDE.md` of every directory you touch; load `code-style` before the first edit;
> implement exactly the plan, its recommended option, re-locating code by the quoted snippets; where a plan says
> "write this test first; drop this plan if it passes without the fix", do that, and if it passes, revert, leave the
> plan file, and move on; never improvise another fix. A file or function already past its size limit (`SongLyrics`,
> `SongSectionContent`, `SongSectionsLayout`, `SectionGrid.kt`) may not end a commit longer than it began. Add the
> tests the plan asks for, run each new defect-pinning test once without the fix and see it fail (mention it only in
> your report). Update the `CLAUDE.md` lines the plan names; edit a shared file only where the README says your lane
> may. Verify with `<commands>`. A plan's "confirm the gain" measurement is an untracked test you delete afterwards;
> report its numbers, never commit it. `git rm` the plan file in the same commit as its fix. Load `commit-messages`
> and commit with a single `-m` sentence in the repo's voice (e.g. `Lay out each fret of a tab once, however often it
> repeats.`), then check the raw commit as section 4, step 6 says and amend on the spot if it fails, and run
> `git -C <worktree> show --stat HEAD` to confirm it touched only that plan's files. Rules: every command starts with
> `cd <worktree> &&` or uses `git -C <worktree>`; scratch files only in `<scratchpad>/lane-<x>/`, never another lane's
> folder; never `git stash`, branch, merge, rebase, push, or bump the version; no Gradle in the main checkout. Report:
> `NN: <hash> "<message>" — verified: <commands>; measured: <numbers, where the plan asks>`, then `Not done: NN —
> <why>`, then the final test result.

### Verification commands per lane

- **N**: per plan `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`, plus
  `:libs:abc:implementation:desktopTest :libs:abc:api:compileKotlinDesktop` for plans 03 and 14; plans 05, 20, 02, and
  01 also `./gradlew :tools:screenshots:test --tests '*songDetails*'`, plan 20 also `--tests '*SheetMusicColumnTest*'`
  (in `ui/songView`, which the songDetails filter misses), and plan 21 `--tests '*ColumnTurnTest*'`. At the lane's end
  `:app:android:compileDebugKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64` and the whole `:tools:screenshots:test`.
- **D**: per plan `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs`; at the
  end `:app:android:compileDebugKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64`.
- **T**: per plan `./gradlew :libs:chordpro:desktopTest :presentation:desktopTest :app:desktop:compileKotlin
  :app:web:compileKotlinWasmJs`; at the end `:app:android:compileDebugKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64`.
- **P**: `./gradlew :presentation:desktopTest :app:desktop:compileKotlin :app:web:compileKotlinWasmJs
  :app:android:compileDebugKotlin`.
- **G**: `./gradlew :app:desktop:compileKotlin :app:web:compileKotlinWasmJs` and the whole `:tools:screenshots:test`.

A corrupt incremental cache is retried with `-Pkotlin.incremental=false`. No lane needs `local.properties`.

## Merging

In the order **T, G, P, D, N**, as lanes report: list each lane's commits' paths
(`git -C <worktree> log --stat --format=%s $START..HEAD`) and stop on any outside the lane's files; then in the main
checkout `git cherry-pick $START..<lane HEAD>`. Conflicts: `NotationCache.kt` (D's `notationWordsOf`, N's `keyOf`) keep
both hunks; `notation/CLAUDE.md` and `print/CLAUDE.md` merged word by word, every sentence of both sides kept. After each
lane run `./gradlew desktopTest`; after the last, the full build:
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS`
and `./gradlew :tools:screenshots:test`. A break is fixed by one more commit, never by rewriting landed ones. Then
`git worktree remove ../Campfire-lane-<x>` for each.

## Finish

- `git log --format=%B $START..HEAD | grep -ciE 'co-authored|generated with|claude code|anthropic\.com'` prints 0;
  `git log --oneline $START..HEAD` is one line per landed plan (plus the plans commit and any build-fix commit);
  `git status` is clean; `git worktree list` shows only the checkout.
- Plan files still present are the skipped ones: trim the README to them and ask the user whether to keep the folder.
  With none left (only `README.md` and `EXECUTION.md`), delete `documentation/issues/` and commit
  `Remove the review plans.` without asking.
- Report the commit count, the measured gains the lanes reported, and the skipped plans with reasons. Update the
  `56th-review.md` memory with the commit range and what was skipped. Do not push.
