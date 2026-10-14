<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Thirtieth review — structure, packages, and CLAUDE.md files

Reviewed at `15bad8bca` on `master`, 2026-10-14. Angle: file and package organization, cohesion, SOLID, so that the
`CLAUDE.md` files can follow packages; plus the standing `CLAUDE.md` review. Plans written and challenged at `489d08c59`.

## Headlines

- `CampfireViewModel` had grown back from 1,377 to 1,830 lines; about 400 of them real logic again (plans 01–05).
- The song rendering engine lives in the song details screen's package and is imported by the editor and dialogs
  (plan 11); the PDF re-implements three of its rules and two have drifted (plan 06).
- 135 presentation files call `:chordpro` directly while 11 use cases only forward to it (plan 08, user decision).
- Flat packages of 110 (`components`), 82 (`songDetails`), 63 (`dialogs`), 52/48 (`domain`), 38 (`data/model`) files
  (plans 11–17).

## Fixed during the review

91 commits, `15bad8bca..489d08c59` (`git log --oneline 15bad8bca..489d08c59`): about 40 docs fixes (false statements,
rules stated twice, module files cut to budget), 45 behaviour-preserving refactors (dead code, the six oversized
screen files split with their decisions tested, the settings page kit shared, use case impls internal, platform file
names), the size check (`.github/scripts/check_sizes.py`, edit hook, pre-commit hook, CI), and one commit restoring
seven notes the docs cuts had dropped. A fresh read of the whole range found no behaviour change.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Move song file operations out of the view model | medium | V |
| 02 | Give saved dialog restoring one owner | low | V |
| 03 | Separate launch state from first run | low | V |
| 04 | Split the import report state from the import queue | low | V |
| 05 | Move sync consent routing into navigation | low | V |
| 06 | Make the PDF lay out sections and runs as the screen does | medium (PDF output changes) | P |
| 07 | Make the song layout search and placement testable | medium | P |
| 08 | Delete the ChordPro use cases that only forward | low | V |
| 09 | Count tags and languages by one rule | low (picker order changes) | V |
| 10 | Name song files and recover errors in one place | low | D |
| 11 | Move the song rendering engine out of song details | low | R |
| 12 | Give components and dialogs subpackages | low | R |
| 13 | Give state, chrome, and screen packages subpackages | low | R |
| 14 | Split the ChordPro module by concern | low | R |
| 15 | Group the domain use cases by feature | low | R |
| 16 | Group the data model by feature | low | R |
| 17 | Group sync and the remote contracts by role | low | R |

## Lanes and merge order

| Lane | Plans, in order | Area |
|---|---|---|
| D | 10 | `:data:model`, local source file names, print file name, the two `Recovering` copies |
| P | 06 → 07 | `print/`, `songLayout/`, `screens/songDetails` layout files |
| V | 01 → 02 → 03 → 04 → 05 → 08 → 09 | `CampfireViewModel`, `ui/state`, navigation, first run, `:domain` use cases |
| R | 11 → 12 → 13 → 14 → 15 → 17 → 16 | every package; cut from `HEAD` after D, P and V merged |

Wave 1 runs D, P and V (at most two Gradle builds at once: start D when P or V finishes). Merge order **D, P, V**: D
touches the least, V the most shared UI files. Wave 2 is lane R alone, cut from the merged `HEAD`, because its moves
touch every file the others edit; 16 runs last of all.

Why 08 and 09 are in lane V: both edit `CampfireViewModel.kt` and its allowance key, as do 01–05.

## Shared-file rules

- `.github/scripts/size_allowances.json`: every commit that shrinks a file with an allowance runs
  `python3 .github/scripts/check_sizes.py --update` and includes the file; a move renames its key with the same value
  in the same commit (`--update` would drop it). On a merge conflict here, take either side and rerun `--update`.
- `screens/songDetails/CLAUDE.md` (lanes P and V both touch it before 11 splits it) and `strings.xml`: word-level
  three-way merge, both sides kept.
- The pre-commit hook runs the license header and size checks on every commit; a lane never bypasses it.

## Decisions

Taken by the user (2026-10-14):
- `:presentation` may call pure `:chordpro` functions directly; forwarding use cases are deleted (08).
- Restructure everything into feature subpackages, `data/model` included (11–17).
- The PDF follows the screen's rules through shared code (06).
- The unreachable import-from-address chain stays for the planned shared links feature.

Taken by default (recommended answers; override any before execution):
- 06: back-to-back tab environments under one label print as one run (the screen's rule; changing the screen would
  renumber saved folds); a break inside a section still starts a new PDF column; line-level differences (annotations
  with chords hidden) stay out of scope.
- 07 lands before the pedal coverage test exists; its side-by-side equivalence check is the safety net.
- 08 keeps `SetChordProTagUseCase` (it adds the library's NFC fold); the preference mappers move to a new
  `presentation/mapper/ChordProMappers.kt`.
- 09: the picker's tag chips follow the songs screen's accent-insensitive order.
- 04 keeps `deleteLibrary` in `ImportController` (deletion and import guard each other); 05 keeps `connectSyncProvider`
  as two lines.
- 11 keeps `screens/songDetails/CLAUDE.md` at about 750 words on its shrinking allowance.
- 12: `TunerSheet` to `dialogs/instrument`; the `ChordsIcon` doc line is dropped. 13: `ContentEdges` to `ui/chrome`.
- 14: `edit/` and `convert/` get CLAUDE.md files of their own. 15: no `useCases` level (`domain.api.<feature>`),
  `SetChordProTag` in `songs`. 16: a seventh package, `setlist`; `ExportedFile` in `library`. 17: `CoverArtDownload` at
  the remote API root.

## Challenge

Every plan was challenged by agents that did not write it; all 17 came back amended, none dropped. Notable catches: a
plan 01 test that proved nothing, plan 04's size claim (534 lines, not 470), a plan 06 commit that would not compile
and a label that could be stranded at a column's foot, two plan 07 tests that would have failed at `HEAD`, three files
plan 09 missed, and the missing ordering of 08/09 before 11–13.

## Checked and found solid

`navigation/`, `update/`, the `platform/` expect/actual seams, `onboarding/`, `metronome`/`tuner` modules, `print/`'s
pipeline (no dependency on the details screen), `:data:formats`, `:data:source:local:implementation`, the three tuning
enums (deliberate, cross-checked by `InstrumentsTest`), `DemoLibraryRepositoryImpl` in `:data:sync`.

## Not planned

- Moving the PDF writer and deflater into `:data:formats` (low value, a new dependency edge).
- A `ThemeFade` class for `CampfireTheme`'s fade (low value, subtle first-frame behaviour).
- Renaming `importReport` to `import` (navigation, browser routes, saved state); 13 adds subpackages only.
- Regenerating the Baseline Profile (prepare-release does it).
