<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Tenth review: the code since the ninth

**Reviewed commit:** `9ab7ca54e` on `master`, clean tree. **Angle:** bugs in the 61 commits since `2c0ef872`, which no
sweep had seen — the song details rework (pedal paging, columns, one row at a time, circular transposition, chorus
folding), Manage tags, setlist countdowns, deleting the library and the cover cache, and the publishing workflows.
**Budget:** lean — four Sonnet area reviewers (song details; lists and dialogs; data and domain; shells, CI and docs)
and one Opus live run of the desktop build in a worktree, driving a seven-song stress setlist with Up and Down alone
at five window sizes and three text sizes. Every finding was re-read against `9ab7ca54e` before it became a plan; the
pure ones were fuzzed (200 000 random songs through the stepping, 60 000 through the section cutting, 100 000 through
the tab wrapper) or proved with throwaway probes in the scratchpad.

## Headlines

The pedal guarantee — every line of any song reachable with Up and Down alone — **holds on the happy path**: in 15
size × text-size combinations and 7 songs (7 747 rests each way) no line was skipped, no hand-off came early, nothing
scrolled sideways, and the tablature wrapped. It breaks around the edges:

- **15** (high) — changing the text size inside a section taller than the screen keeps a *pixel* offset into it, so
  the reader lands on other lines: 12 lines back at 1.8, then 30 lines forward at 0.6, and the lines in between are
  skipped for good.
- **16** (high) — a chord that does not fit at the end of a wrapped chord-only line is drawn over the one before it,
  and an annotation wider than the column is drawn off the screen and into the *next song's* pager page.
- **17** (medium) — a held Up or Down (the OS's key repeat, half a second in) chains 19 full steps, so pages fly past
  unread.
- **18** (medium) — Up at the top of a song hands off to the previous song's *top*, so a second Up skips that song
  whole.
- **11** (medium) — a one-row song that scrolls only because of its header gets no room for the step buttons, which
  then sit over the ends of its lines.
- **01** (medium) — the library deletion's "delete from the cloud too" answer is dropped when a sync run is already
  going, and Settings asks the question the user just answered.
- **03** (medium) — a tag spelled `createTag` crashes the Manage tags dialog on a keystroke (duplicate lazy list key).

Three decisions are open (below); everything else is ready to execute.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Stop a running sync run before deleting the library, so that the cloud deletion the dialog promised is carried out | medium | C |
| 02 | Take an undated incoming setlist as silent about the countdown too, and keep the library's countdown when it replaces one | low | C |
| 03 | Give the Manage tags dialog's create row a key no tag can have | medium | B |
| 04 | Put a typed tag that spells an existing one on the song when Done is tapped | low | B |
| 05 | Let a tag filter that changes nothing in the list consume its anchor, instead of leaving it to jump the list later | low | B |
| ~~06~~ | ~~Keep the delete-library row disabled while a deletion runs~~ — dropped by the challenge, see below | — | — |
| 07 | Settle the references to the release check that no longer exists (**D1**) | medium | D |
| 08 | Say when a store script fix reaches a tagged release, and stop the Mac badge pointing at the releases page | low | D |
| 09 | Stop the release on a release-description comment it cannot read, and on notes a store will refuse | low | D |
| 10 | Bring the presentation docs up to date with the website and the tags dialog | low | B |
| 11 | Count the header when deciding whether a song leaves room for the step buttons | medium | A |
| 12 | Keep the section-cutting search off a songbook-sized file, and look a unit's section up in constant time | low | A |
| 13 | Tell a screen reader where the step progress indicator is | low | A |
| 14 | Bring the song details docs in line with the one-row default, the Safari pinch and the layout budget (**D3**) | low | A |
| 15 | Keep the reader on the same line when the text size changes inside a section taller than the screen | high | A |
| 16 | Keep chords and annotations inside their line and their page | high | A |
| 17 | Take a held Up or Down key as one step, not as a step per repeat | medium | A |
| 18 | Land on the previous song's end when Up hands off backwards | medium | A |
| 19 | Never let a step move the song by less than a line | low | A |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| C | data and domain | 01, 02 | `domain/api` (`DeleteLibraryUseCase.kt`); `domain/implementation` (`DeleteLibraryUseCaseImpl.kt`, `SyncUseCaseImpls.kt`, `ImportPlanner.kt`, `ImportFilesUseCaseImpl.kt`, their tests, the new `DeleteLibraryUseCaseImplTest.kt`); `data/repository/api` `SyncRepository.kt` and `data/repository/implementation` `SyncRepositoryImpl.kt`; root `CLAUDE.md` line 545 (the library deletion sentence); `domain/implementation/CLAUDE.md` (the setlist comparison and the deletion) |
| B | presentation: lists, dialogs, Settings | 03, 04, 10, 05 | `presentation/**` except `ui/screens/songDetails/` (`dialogs/Dialogs.kt`, `ui/CampfireViewModel.kt` outside the `magnifySongText` KDoc, `screens/songs/SongsScreen.kt`, `components/ListItemAnimation.kt`); `domain/api` `models/ScreenData.kt`; `domain/implementation` `GetScreenDataUseCaseImpl.kt` and its test; `presentation/CLAUDE.md` line 50 and its song list paragraph; `domain/implementation/CLAUDE.md` (the song list) |
| D | CI, shells, docs | 07, 08, 09 | `.github/**`; `README.md`; root `CLAUDE.md` lines 286 and 351 and the release-description paragraph; `.claude/skills/codebase-review/SKILL.md`, `.claude/skills/release-notes/SKILL.md`; `presentation/CLAUDE.md` line 132 only |
| A | song details | 15, 16, 11, 17, 18, 19, 12, 13, 14 | `presentation/src/**/ui/screens/songDetails/**` and their tests; `values/strings.xml` and `values-hu/strings.xml` (13 only); the `magnifySongText` KDoc in `CampfireViewModel.kt` (14 only); `presentation/CLAUDE.md` lines 19 and 116 and its `songDetails` paragraphs |

**Merge order: C, B, D, A.** C first, since 01 changes `SyncRepository.synchronize`'s signature and 02 the import;
B next, since 05 changes `ScreenData`; D is documentation and workflows; A last, because it touches the most UI files
and the most `presentation/CLAUDE.md` paragraphs.

**Shared files.** Root `CLAUDE.md`: C (line 545) and D (lines 286, 351, the comment-format paragraph) — different
paragraphs, merged word by word if they collide. `presentation/CLAUDE.md`: every lane, always different one-line
paragraphs; resolve as a word-level three-way merge keeping every sentence of both sides, never one side whole.
`domain/implementation/CLAUDE.md`: C (02) and B (05), different sentences. `CampfireViewModel.kt`: B (05, 06, 10) and
A (14, one KDoc), distinct hunks. `strings.xml`: A only. No two lanes touch the same Kotlin function.

## Decisions

- **D1 (plan 07): drop the references to `documentation/testing/release-check.md`, or restore it?** Recommended:
  **drop them** (option A). The user removed the folder as outdated on 2026-09-26 (`b0e98cfa8`), four days after
  asking for it; the root `CLAUDE.md`, the review skill and `presentation/CLAUDE.md` still name it and another deleted
  file. Option B restores the check from `b0e98cfa8^` and updates it for the pedal flow, Manage tags and the library
  deletion. **Decided 2026-09-30: option A, drop the references.**
- **D2 (no plan): in a single column, keep one press per section?** The live run needed 290 presses for a
  300-section song of one-line sections at 500 × 900 (one press per section, 10–13 lines visible), where a multi-column
  layout of the same song took 4–31. Recommended: **keep it** — a section per press is the design, real sections are
  4–8 lines, and the next section arriving at the top is what a reader expects; plan 19 removes the presses that move
  only a few pixels. The alternative is to page by screen in a single column too, snapping to the furthest section top
  inside the window (a plan would be written for it). **Decided 2026-09-30: keep one press per section; no plan.**
- **D3 (plan 14): say that Safari's trackpad pinch is left to the page, or add the `gesturechange` listener?**
  Recommended: **the sentence** — the docs claim a handling that does not exist, and the app has no Safari-specific
  code today. The listener is about twenty lines in `CampfireWebApp.kt` plus a pure ratio helper, described in the
  plan, if the user wants the feature. **Decided 2026-09-30: the sentence; no listener.**

## Challenge

Two fresh agents that had not written the plans tried to break every fix: one (Sonnet) for lanes C, B and D, one
(Opus) for lane A, with probes of the changed pure functions against the reviewers' fuzz scenarios. Four plans came
back sound (03, 08, 14, 18), one was dropped (06) and the rest amended; each amended plan says what changed under its
title. The changes that matter:

- **15:** the line-based anchor was rewritten — the reviewer's version indexed lines within a *stop* (a row's first
  section), which points into another section after a re-layout, and clamped the reader forward as the text shrank.
  `SongRows` now also carries `lineBottoms` and `lineSections`, the anchor is the line under the top of the reading
  window with a proportional offset, the old anchor stays as the fallback. Probed at 20 ↔ 36 ↔ 60 px lines both ways.
- **19:** the "go on to the next stop" rule skipped a tall section's pages (6 732 of the fuzzed songs); the rule is
  now a walk of ordinary steps that stays within one screen of the reader, proved on 400 000 songs both ways with no
  skip and 40–60 % fewer sub-line presses. The overlap cap changes one existing test's number.
- **16:** the break opportunity is placed only at a word boundary of the original text, and whitespace at either end
  of a padded fragment is made non-breaking (the plan's own example would otherwise still have broken before the
  padding); the annotation wrap was dropped as unbuildable in the draw pass — wide annotations are clipped, the
  content description keeps the text; the chord clamp is bounded by the previous chord's edge.
- **11:** both candidate grids (inset and full width) are tested under the header, not only the inset one; the
  header term drops the half gap.
- **17:** the release is handled before the modifier check and the held set is also cleared when the *window*
  loses focus (`isWindowFocused`), since Compose focus does not report that.
- **13:** the stop is read through `derivedStateOf` (a composition read of `stopProgress` would recompose per
  pixel), and the string goes through `stringResource`.
- **01:** `synchronize` returning `Boolean` needs `SynchronizeLibraryUseCaseImpl` changed too (expression body); the
  lock description was corrected (the new run waits on the repository's own mutex, not `LibraryFileLock`).
- **02:** dates and the countdown shipped in different builds, so only *undated* files are tolerated; a dated file
  from the date-only build still asks. The replace test was unreachable and was rewritten.
- **05:** `SongListPreferences` is private to the use case implementation; `ScreenData` gets four plain fields instead,
  carried through `SongPart`; the test now has a case that fails with the bug in place.
- **09:** both store scripts check the length before `prepare_only`, so the length errors apply whatever `submit`
  says; the 4 000 limit covers `mac-app-store` too; the `tests.yml` step goes before the Gradle step.
- **04, 07, 10, 12:** a `List` not a `Set`, no pointer at a private memory note, a KDoc link that resolves to nothing,
  the cap counts sections not chunks.
- **06 dropped:** `deleteAllSongs` takes the lock and only then lists the folder, so a second typed `DELETE` deletes
  nothing and shows no failure; a guard against a no-op is not worth a state flow.

Within lane A: 15 and 19 change different functions of `RowSnapping.kt`; 11 and 12 both change `searchGrid` (12 is
applied on top of 11, and its early return carries the height 11 adds); 13 reads the stops 19 merges, and runs after
it.

## Checked and found solid

- **Pedal stepping** (`nextStepTarget`, `previousStepTarget`, `SongStepper.step`, `keepReaderInPlace` across
  resizes, `RowSnapFlingBehavior`): 200 000 fuzzed songs and the live run agree — no skipped line, no early hand-off,
  the last stop clamped to `maxValue`, two quick presses at a song end never skip the next song, held presses never
  lost or doubled, window resizes keep the line. The one failure was an 80 px viewport, unreachable on any screen.
- **Section cutting** (`flowIntoRowsCuttingSections`, 60 000 cases): rows ascend, no section crosses a row, cuts only
  where allowed, every multi-column row within `maxRowHeight`. **Tab wrapper** (100 000 cases): no character lost, no
  row wider than allowed. `spacedEvenly` never exceeds its region, RTL mirrors LTR.
- Circular transposition (`wrapTransposition` and the legacy stored values), the font scale accumulator and the wheel
  accumulation, the touchpad pinch on the desktop (`TouchpadMagnification.kt`: no-op off macOS, `--add-exports` only on
  a Mac host, EDT, listener removed on dispose, failures caught), chorus card folding, the `EdgeFade` rewrite, the
  action overlap maths, the Up/Down/PageUp/PageDown key handling per platform, the `LayoutBudget` cut.
- **Lists and dialogs:** `textResource` for every user-written text; tag case and Unicode handling; dialog state
  across rotation; the typed `DELETE` (same word in both languages, guarded against a double tap, no song or editor
  screen on the back stack); `RelativeTime` day arithmetic (DST, zones, year boundary, the midnight refresh) and its
  Hungarian plurals; the fast scroller and its test; the setlist date picker (UTC both ways); the web touch fix; the
  Microsoft Store link. The 388 string keys match between the two languages.
- **Data and domain:** the deletion order and the cached lists, a file that cannot be deleted staying listed and in
  the cloud, the guard waiver being one run's parameter, the countdown field not changing old setlist bytes (so sync
  does not re-upload them), the preferences default and unknown-key handling, the cover cache size off the main
  thread and conflated. No JVM-only API in new common code.
- **CI and shells:** `tests.yml` gating (`needs: [prepare, test]` on every store job, fork PRs get no secrets, the
  nightly on the default branch), the tag and build-number checks, `DROPBOX_APP_KEY` refusals, release notes never
  interpolated into a `run:` block, `publish-web`'s rsync, the Linux and Windows start checks, the Apple scripts'
  draft handling, the entitlements, the Android and iOS URL opening, the privacy manifest, the web `index.html`.

## Dropped after verification

- **06, keep the delete-library row disabled while a deletion runs** (dropped by the challenge): `deleteAllSongs`
  takes `LibraryFileLock` first and only then lists the folder, so a second typed `DELETE` finds nothing, deletes
  nothing and reports no failure. The plan file stays for the record until the folder is removed.
- **A press handing off past a song whose page is not laid out yet** (song details reviewer, speculative): the live
  run's two quick Downs at a song's end stepped inside the next song every time; not reproduced.
- **A pasted tag is cut to 30 characters without a message**: accepted — the create row shows the cut text before it
  is confirmed, and tags in files have no limit either way.
- **The root `CLAUDE.md` says the deletion starts a run "with the deletions allowed" without a caveat**: superseded
  by plan 01, which makes the sentence true (the plan updates it).
- **The web app's move to `campfire-songbook.com/app/`**: not a code finding — the code and `app/web/CLAUDE.md` agree
  on the new redirect URI. What is owed is outside the repository (see Manual checks).
- **The Microsoft Store draft amendment re-adding a package under the same file name**: could not be verified
  offline; owed as a check at the next prepare-then-submit release rather than changed blind.
- **Up does not retrace Down's rests** (45 of 105 song × combination pairs page back to different rests): both walks
  cover every line, which is the requirement; a per-page stack of rests would make Up show the pages Down showed, and
  is there to ask for.
- **The step animations ignore reduce-motion**, and **the committed Baseline Profile names removed members**
  (`isHorizontalSectionFlowEnabled`, `AddSongTag`): noted; the profile is regenerated before a release as the docs say.

## Measurements

From the live run (desktop JVM, debug build, `-Duser.home` isolated, `9ab7ca54e`):

| What | Value |
|------|-------|
| Presses to read a 400-line single section, 1400×800 / 800×600 / 500×900 / 1000×450 / 360×300, text 1.0 | 28 / 41 / 25 / 68 / 201 |
| Presses for 300 one-line sections, single column at 500×900 | 290 (D2) |
| Presses for the same song, multi-column layouts | 4–31 |
| Mean step as a share of the visible height (all but the single-column case) | 0.5–1.1 |
| Freeze composing a 300-section song or its neighbour, 1400×800, text 0.5 | 3.0–4.2 s (plan 12) |
| The same at text 1.0 | 405 ms |
| Peak JVM RSS over the run | 878 MB |
| `flowIntoRowsCuttingSections`, 1 000 sections, both passes (probe, JVM) | 0.74 s |

## Manual checks owed

Per plan, in each plan's last section. Beyond them:

- **Dropbox console:** `https://campfire-songbook.com/app/` must be among the app's redirect URIs (Dropbox matches
  them exactly); the web build's Connect fails with a redirect mismatch otherwise.
- **The old web address:** OPFS is per origin, so a user of the previous address opens the new one with an empty
  library and no sync connection; a note on the old address or in the release notes is the only way to tell them.
- **Microsoft Store:** the prepare-then-submit flow (`submit` off, then on) once against the real store, to see
  whether Partner Center accepts a package re-added under the same file name.
- **Baseline Profile:** regenerate before the next release; the committed one names removed members.
- **Plan 12:** a 300-section song at 1400 × 800 and text size 0.5 after the cap — measure the remaining freeze.
- **Plan 16 and 17 on a phone:** a wrapped chord-only line, and a pedal held down, on Android and iOS.
