<!--
  This file is part of Campfire.
  Copyright (c) Pandula Péter 2017-2026.
  https://github.com/pandulapeter/campfire

  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  https://mozilla.org/MPL/2.0/.
-->
# Thirty-first review

Reviewed `0f47f4bc9` on `master`. Angle: everything landed since the thirtieth sweep (bundled fonts, the onboarding
finish animation, the offscreen UI tests and goldens, the large-text fixes), plus the quality of the test suites and
where UI test coverage should grow. Lean budget: four Opus area reviewers, the CLAUDE.md reviewer, no live run.

## Headlines

- No data-loss bug in the product code. The fonts, the intro animation, and the recent fixes held up.
- The tests had blind spots: the sync fake deleted whatever revision it was sent, so "an edit beats a deletion" in the
  race with another device was untested (a mutation dropping the revision passed all 263 sync tests); the selection
  accessibility test's "unticked" check matched no node and passed on nothing; two "nothing was written" checks raced
  the IO write. Fixed during the review, except the import one (plan 02).
- Coverage: the running app's main writes (editor save, setlist overrides, renames, first run) have no end-to-end test.
  Plans 03–08 add them, asserting on library files and semantics rather than more goldens.

## Fixed during the review (`0f47f4bc9..de4179642`, 20 commits)

- `de4179642` Keep the stack trace of a golden case that cannot be drawn.
- `8c325eb61` Rewrap the fake cloud folder's KDoc.

- `c3cff5b92` Make the fake cloud folder's Dropbox mode take two Unicode forms of a name for one file, as Dropbox does.
- `b186a5d34` Fail the golden renders on an image no case draws any more.
- `d24f548fd` Draw every golden case even when one of them cannot be driven.
- `8f77565b2` Make the cancelled new song test wait for a later write before saying nothing was written.
- `1db31d3e2` Make the selection accessibility test fail when it finds no card, and find the unticked one.
- `c57af1488` Remove the fake cloud folder's unreachable case check.
- `a8c3ee241` Test that a file edited on another device while this one deletes it is brought back.
- `d2be453af` Keep a file name the same when normalized twice, when an apostrophe sat before a non-Latin accent.
- `81a054e87` Fix screen readers missing the names of the Done reordering and PDF Save buttons.
- `33a095fda` Rewrap the platform impersonation KDoc.
- `0195c0011` Fix the open source list's KDoc, which still said only the web build bundles fonts.
- `41c13ff94` Correct the font notes, which said Inter carries the sharp and flat signs.
- `21f710aae` Point the code style's test rules at the offscreen UI tests.
- `5fb2c9d20` Let review plans ask for a UI test where only the running app shows the fix.
- `633657d34` Turn the sync notes' test description into a rule for cross-device cases.
- `67c978b13` Drop the UI test notes' repeated line about bundled fonts.
- `d3ca551a2` Stop the platform notes claiming the desktop fonts follow the impersonated platform.
- `dbc005bf3` Fix the metronome notes' self-referencing accent link.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Let the screens call the holders instead of the view model's forwarders | low | V |
| 02 | Make the Replace import test prove nothing is written before the answer | medium | U |
| 03 | UI test: the editor's Save, Ctrl+S, and Discard reach (or leave) the song file | medium | U |
| 04 | UI test: a setlist override is written to the setlist only | medium | U |
| 05 | UI test: Update file name follows into setlists | low | U |
| 07 | First-run UI test (harness flag + onboarding, demo once, What's new held back) | medium | U |
| 08 | Every text-field sheet stays above the keyboard, by semantics | low | U |
| 09 | Two-device sync cases: rename vs edit, wipe guard across devices, Unicode forms | medium | S |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| S | sync tests | 09 | `data/sync/implementation/src/commonTest/**` |
| V | view model facade | 01 | `presentation/src/**` (callers of the forwarders, `CampfireViewModel.kt`, `DialogType.kt` KDoc, three `ui/**/CLAUDE.md`), `tools/screenshots/src/main/**` |
| U | UI tests | 02, 03, 04, 05, 08, 07 | `tools/screenshots/src/test/**`, `tools/screenshots/CLAUDE.md` |

**Merge order: S, V, U.** S and V touch nothing of U's (V edits `tools/screenshots/src/main`, U only `src/test`). U goes last because its new tests drive the screens V
rewires (call sites only, no behaviour change), so its tests run once more against the merged code. 07 is last in U
because it changes the harness (`AppTest.kt`) the other U plans use.

Shared files: `.github/scripts/size_allowances.json` (V lowers allowances with `check_sizes.py --update`; on a conflict
rerun the script after the cherry-pick rather than merging numbers by hand). No `strings.xml` changes.

## Challenge

Every plan was challenged by a fresh agent before this report. 02 sound. 01 amended (`:tools:screenshots` is a friend
module, so only `:app:desktop`'s callers keep forwarders; method references, extension receivers, and `DialogType` KDoc
added). 09 amended (rename case starts from three songs, since one deletion of a one-song index trips the wipe guard;
exact guard counts; `syncResult` beside `sync`). 03, 04, 05, 07, 08 amended with the real packages, strings, openers,
and fixtures; 07 pins the English locale and drops the relaunch case (the harness deletes its home); 08 lists every
text-field sheet. 06 dropped (below).

## Decisions

- **Compare goldens coarsely on a Mac?** Goldens are compared only on Linux (`Goldens.kt`: `isComparing =
  os.name.startsWith("Linux")`), so a layout regression shows only in CI. Option: off Linux, fail on pixels differing by
  more than 64 over ~2% of the image (Mac vs Linux peaks near 1%). **Decided: no** (user, 2026-10-14) — CI already catches
  it, and a second threshold is a second thing to tune.

## Checked and found solid

- Fonts: first frame already in Inter on Android/desktop/iOS (blocking load, cached); web waits for the three Inter
  weights with the 5 s fallback; Hungarian, extended Latin, Cyrillic, Greek covered; every `Text` goes through the
  theme; chord diagrams and the PDF take the bundled families; OFL credited for every build; ~740 KB of fonts.
- Onboarding finish animation: runs once (state flag, reset on reopen, not restored after process death), no input
  blocking, draw-phase-only animation, rotation-safe, monkey-tap guarded by state, Android animator scale 0 snaps.
- Page turner step-back fix and its forward mirror; the stacked control label; the ticked-state semantics; the holder
  refactor of c590c7665.
- UI test harness: isolated home per test, no sleeps, Koin stopped and Coil reset between tests, goldens settle on
  three identical captures, tolerance tight enough for a 1 dp shift; the suite runs in about a minute.
- Unit tests: `TwoDeviceSyncTest` caught a mutation that 255 single-device tests missed; planner and `DeletionGuard`
  branch coverage; engine race tests; import planner (~70 cases); metronome 10,000-bar drift; tuner synthetic signals.

## Not planned (low value)

- UI tests for search wiring and selection-mode rules (the logic is unit tested; low regression risk).
- Pinning the UI tests' time zone: no test depends on the host clock today; keep the dated-fixture rule.
- `PlayingValuesGroup` squeezing its metronome button at a large text size: sized just under the width; not filed
  unless seen on a device.

## Dropped after verification

- Plan 06, "feature switches only hide" UI test: every switch is a one-line preference copy, so "never writes" holds by
  construction, and what each hides is unit tested (`FeatureDestinations`); dropped by the challenge.

- "Docs name `CampfireViewModel` pass-throughs" (`ui/print`, `ui/screens/settings`, `ui/screens/setlists`
  `CLAUDE.md`): the forwarders exist, so the docs are true today; plan 01 updates them as it removes them.
