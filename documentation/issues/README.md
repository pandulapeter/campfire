<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# 54th review: the foundation

Reviewed at `c7807015a` on `master`. The angle is structural, not a bug hunt: architecture, modularization, God
objects, the test harness, and the processes the next years of work build on. Four reviewers (module graph, state
ownership, the layers below the UI, and tests, checks, and process), three plan writers, and two challengers.

## Headlines

- **The logic is well factored; the wiring is not.** About 35 focused holders, most of them unit-tested; a clean
  module graph; sync owned by the data layer. The God object is `CampfireViewModel` as a *composition root*: 57 use
  cases, 22 `start…()` calls whose omission silently kills a feature, 17 anonymous listeners that hold the "stops when
  the screen goes" rules, and construction order that is behaviour. It is then handed to about 100 composable files as
  a service locator. Plans 12 → 13 → 14.
- **Nothing inside `:presentation` enforces a direction.** 51 of 54 `ui/` package groups form one import cycle. The
  code-style skill already forbade composables taking the view model, and the code drifted anyway: prose rules do not
  hold, checks do. Plan 11.
- **The chord-sheet importer is ordered rules with no metric.** About 60 commits in a week, each fixing one document
  with another first-match clause; nothing shows what a fix does to other documents. Measure first (05), then
  restructure (06, 07).
- **The harness lets red reach master.** Unit tests ran only in CI (fixed during the review: pre-commit runs them now);
  golden renders could fail only on Linux CI (01); one test binds a fixed port, which now blocks parallel commits (02).
- **Splitting `:presentation` would not pay for compile time today**; measure before anyone splits it (20).

## Fixed during the review

- `c7807015a` Remove the review plans.
- `fe59c392d` Let the regex check skip a tracked Kotlin file deleted in the working tree instead of crashing the
  pre-commit hook.
- `fa9b4dbf7` Test that every language names exactly the default strings' keys.
- `1c5731ed1` Remove the code-style reminder added to every Kotlin and strings edit, which the skill and the checks
  already cover.
- `76f3a2c9b` Run the unit tests before every commit that stages more than Markdown.
- `adfc215ae` Let leaving the About dialog stop its license reads instead of reading on with empty texts. (This
  replaced plan 09: the sweep of 69 broad catches in data and domain found no other swallowed cancellation.)

## Priorities

| Tier | Plans | Why first |
|---|---|---|
| **P1** | 02 (step 1), 10, 01, 11, 12, 13, 05 | Stop red reaching master, guard stored formats, enforce direction, put a net under the wiring, then fix the wiring's riskiest part; measure the importer before it changes again |
| **P2** | 14, 15, 16, 04, 06, 07 | The service-locator removal, persistence by construction, import rules below the UI, platform storage tests, the importer redesign (07 needs the corpus filled) |
| **P3** | 02 (step 2), 03, 08, 20 | Cheap guards and a measurement |

## Index

| # | Plan | Priority | Effort | Lane |
|---|---|---|---|---|
| 01 | Replace the Linux-only golden renders with assertions that run on every OS | P1 | L | H |
| 02 | Make the unit tests independent of the host (port, bundled Hebrew in the PDF) | P1 / P3 | S / M | H |
| 03 | Fail a test when a public `:chordpro` model class is missing from the stability configuration | P3 | S | H |
| 04 | Run the storage contract tests on the iOS simulator | P2 | M | H |
| 05 | Score the chord-sheet importer against a private corpus | P1 | M | I |
| 06 | Move the chord-sheet converter into a module of its own | P2 | M | I |
| 07 | Restructure the converter into explicit stages, then remove the Campfire-specific rules | P2 | L | I |
| 08 | Make the library file lock refuse reentry | P3 | S | D |
| 10 | Gate setlist documents on their format version; stored fields are add-only | P1 | S | D |
| 11 | Enforce one-way package layers inside `:presentation` | P1 | M | P |
| 12 | Test the exit, pause, and first-run paths before the refactors | P1 | M | P |
| 13 | Turn the view model's listeners into named, tested policies; holders start themselves | P1 | L | P |
| 14 | Give composables what they use instead of the whole view model | P2 | L | P |
| 15 | Move applying an import into `:domain`, the trust rule into `:data:model` | P2 | M | P |
| 16 | Flush every waiting write by construction, on pause too | P2 | M | P |
| 20 | Measure the `:presentation` build before anyone splits it | P3 | S | P |

## Lanes and merge order

| Lane | Area | Order | Owns |
|---|---|---|---|
| H | harness | 02, 01, 03, 04 | `tools/screenshots` tests, `tests.yml`, `gradle/compose-stability.conf`, `DesktopSyncAuthenticator`, `PrintRenderer`'s Hebrew face, storage tests |
| D | stored formats | 10, 08 | setlist document format, `LibraryFileLock` |
| I | importer | 05, 06, 07 | `chordpro/convert`, the new `:chordsheet` module, `:data:formats`, import corpus test |
| P | presentation wiring | 11, 12, 13, 14, 15, 16, 20 | `CampfireViewModel`, `ui/state`, every screen's parameters, `.github/scripts/check_imports.py` |

**Merge order: H, D, I, P.** H first because 01 and 12 both edit `AppTest.kt` and 02 must land before 06 step 2 and
07's `PdfRoundTripTest` cut. H and D run in parallel; I and P are cut from `HEAD` after H merged, and run in parallel.
P last because it touches the most shared UI files.

**Shared files:** root `CLAUDE.md` (04, 05, 10, 11), `data/source/local/implementation/CLAUDE.md` (04, 05, 10),
`gradle.properties` (01, 05), `tests.yml` (01, 04, 11), `.githooks/pre-commit` (11), `PdfRoundTripTest` (02 → 06 → 07),
`ui/CLAUDE.md` (13, 14, 16). Each lane adds its own sentences; a merge keeps both sides' words.

**Gates:** 07 runs only once the corpus at `campfire.importCorpus` holds real documents (05's last step is the
owner's); without it, lane I stops after 06. 13 step 4 moves one holder per commit and leaves a holder whose start
needs an unlisted reorder in the view model. 20 reports numbers; it never splits.

## Decisions

Taken by the owner on 2026-11-02: unit tests run before every commit; the golden renders are replaced; the importer
corpus is private, outside git, behind a `campfire.*` property; the per-edit reminder hook is deleted.

Taken by the orchestrator under the owner's rulings: Hebrew in the PDF is set in the bundled Noto face (the product
rule: bundled fonts only); one `ChordSheet` model; `chordpro.convert` keeps its package; evidence-scored classification;
capo and details-row wording stay general vocabulary; `PdfRoundTripTest` shrinks to one single-column test; only the
storage module's iOS tests run in CI; no pre-commit check for broad catches (it would need an allow list); four
presentation layers; pending values flush when the app goes to the background; the trust rule moves to `:data:model`
as a pure function. None awaits the owner.

## Checked and found solid

The module graph (no `implementation` dependency outside `:app:*`, `:chordpro` dependency-free, Koin graph checked at
compile time); the sync design (`SyncPlanner` a pure function, the engine decomposed, `TwoDeviceSyncTest`); `:data:formats`
hardening; `:chordpro`'s public API; the holders themselves; persistence evolution (tolerant decoding, unknown fields
kept, the synced preferences' version gate); the unit suite (4,943 tests, 31 s re-run, no flake in six runs, one under
full CPU load); the UI suite (92 tests, 77 s, stable); the size, header, and doc-name checks; dependency hygiene (one
alpha, Material 3 Expressive, needed).

## Dropped after verification

- **09** (rethrow cancellation through one helper, refuse broad catches): one real site, fixed in `adfc215ae`; the
  helper exists (`Logger.recovering`); a textual check needs an allow list.
- **17** (per-song settings as one `SongSettings` record): renames and clears are already one function each, and two
  of the maps are not per-song; do it with the next per-song setting.
- **18** (move song analysis into `:chordpro`): the scale and key engine is already there; what is left needs
  presentation types.
- **19** (a scope per back-stack entry and dialog): no observed defect, and 13 names every lifetime rule with a test.
- A full lock-tier assertion (08 keeps only the reentry guard, the one hazard that hangs), a `:presentation:song`
  module, per-screen ViewModels, holder construction in Koin, write-behind in the preferences repository, Konsist or
  detekt, a hard gate on `CLAUDE.md` word budgets.

**Challenge:** ran on every plan. All 16 remaining plans were amended, and 19 was dropped. Notable catches: 02's
"production address" check still bound the fixed port; 07's old step 8 would have committed red tests; 08's reentry
mark had to be per lock or `TwoDeviceSyncTest` would refuse two devices; 11's layer table misplaced the root package and
needed 46 edges moved, not 15; 15 cannot read the trust choices through a suspending use case without losing ticks.

## Measurements

`desktopTest` 55 s cold, 31 s re-run (chordpro 24 s, presentation 19 s, of it `PdfRoundTripTest` 8 s);
`:tools:screenshots:test` 77–80 s (`PedalCoverageTest` 38 s); the pre-commit hook 21 s with a Kotlin change. CI: 22 of
the last 200 `tests.yml` runs failed, most of them UI tests.
