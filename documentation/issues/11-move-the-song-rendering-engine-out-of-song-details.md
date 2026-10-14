<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Move the song rendering engine out of `screens.songDetails` into `ui.songView` and its subpackages

**Challenged:** amended — named plan 07's `SectionPlacement.kt` and its three test files in the tables; the private
`InPlaceSongLyricsModel` moves with `rememberSongLyricsModel`; `SongPreview.kt`'s imports change in commits 1, 3 and 5,
not only 5; plan 08 must land first (it edits `screens/songDetails/CLAUDE.md` and three files here); the
pedal-coverage plan's one CLAUDE.md pointer goes to `stepping/`.

**Kind:** structure · **Severity:** medium · **Platforms:** all (commonMain only; no platform source set has files in
this package)
**Files:** every file in `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/`
and its tests in `presentation/src/commonTest/.../ui/screens/songDetails/` and
`presentation/src/desktopTest/.../ui/screens/songDetails/SongTextMeasurementsTest.kt`; `ui/songInfo/SongInfo.kt`;
`ui/dialogs/SongInfoSheet.kt`, `ui/dialogs/SongLinksDialog.kt`, `ui/screens/songEditor/LoadedSongEditor.kt`,
`ui/screens/songEditor/SongPreview.kt`; the CLAUDE.md files `ui/screens/songDetails/CLAUDE.md`, `ui/chords/CLAUDE.md`,
`ui/metronome/CLAUDE.md`, `ui/playing/CLAUDE.md`, new `ui/songView/CLAUDE.md`, `ui/songView/model/CLAUDE.md`,
`ui/songView/grid/CLAUDE.md`, `ui/songView/firstSection/CLAUDE.md`, `ui/songInfo/CLAUDE.md`,
`ui/screens/songDetails/stepping/CLAUDE.md`, `ui/fontScale/CLAUDE.md`; `.github/scripts/size_allowances.json`;
`documentation/plans/pedal-coverage-test.md`.

(`ui/` below is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`; tests mirror it under
`commonTest`.)

## Problem

`ui/screens/songDetails/` is one flat package of 82 files (12,845 lines) and a 4,535-word CLAUDE.md (the package budget
is 600; it lives on an allowance). It holds four unrelated things: the screen (pager, app bar, keyboard, steppers), the
song renderer that the editor's preview also draws (`SongLyrics` and its tree), the pure layout search (`SectionGrid`,
`SectionCutting`, `LayoutScore`, ...) and the About the song body that two dialogs share. So other features reach into
a screen's package:

```
ui/dialogs/SongInfoSheet.kt:         import ...ui.screens.songDetails.SongDefaults / SongInfoBody / rememberSongInfoEditing
ui/dialogs/SongLinksDialog.kt:       import ...ui.screens.songDetails.linkLabel
ui/screens/songEditor/LoadedSongEditor.kt: import ...ui.screens.songDetails.rememberSongInfoEditing
ui/screens/songEditor/SongPreview.kt: import ...ui.screens.songDetails.ChordDiagrams / SectionMotion / SongInfoEditing /
                                     SongLyrics / SongLyricsInputs / prepareSongLyrics
```

and no package is small enough for a short CLAUDE.md to describe.

## Fix

Behaviour-preserving moves only: no declaration changes except the two file splits named below. Everything stays
`internal` (module-wide, so cross-package use keeps compiling).

**Order.** Land this after plan 08 too, which rewrites a sentence of `screens/songDetails/CLAUDE.md` (split here) and an import
in `SongDetailsPage.kt`, `SongDetailsScreen.kt` and `SongPreview.kt`. Land this after plans 06 and 07, which edit `SectionFolding.kt`, `SongSectionsLayout.kt` and the grid
files; quoting their paths after this move would be stale. The new files those plans create follow the table:
`SongGridSearch.kt`, `SectionPlacement.kt` (which holds `placeSections`) and `SongGridSpec.kt` (if plan 07 split the
spec out as its fallback) go to `songView.grid`, with plan 07's
`FakeSectionMetrics`, `SongGridSearchTest` and `SectionPlacementTest`; anything plan 06 adds lands in
`ui/songLayout/` already and is not moved. If plan 06 emptied `SectionFolding.kt`, skip its row. It does not depend on
the pedal-coverage test (`documentation/plans/pedal-coverage-test.md`): it changes no code paths, and the unit tests
(which all move along) plus the compile checks are the net. Only update that document's file references: its line 54
("One line in `ui/screens/songDetails/CLAUDE.md` pointing at the test") names `ui/screens/songDetails/stepping/CLAUDE.md`
instead; `RowSnappingTest` and `SectionPagingTest` keep their names.

### Target layout

`songView` is the Composable renderer (the screen and the editor's preview both draw it); `songView.model` builds what
it draws (pure); `songView.grid` decides where sections go (pure); `songView.firstSection` is the first section's
controls; `songInfo` is the About the song body; `screens.songDetails.stepping` is the pedal stepping and snapping that
only the screen does.

| Current file (`ui/screens/songDetails/`) | New package (under `ui.`) | Note |
|---|---|---|
| `SongLyrics.kt`, `SongSectionsLayout.kt`, `SongSectionItem.kt`, `SongSectionContent.kt`, `SongChordsSection.kt`, `SongComment.kt`, `SongKeyChange.kt`, `SongTimingLine.kt`, `SongLineWithChords.kt`, `SongGridLine.kt`, `SongTabBlock.kt`, `SectionCards.kt`, `SectionTitle.kt`, `SectionHeaderPill.kt`, `FoldChevron.kt`, `FadingIn.kt`, `SongInstrumentChips.kt`, `ChordShapeCrossfade.kt`, `ChordCellLayouts.kt`, `ShownSections.kt`, `SongLyricsDimensions.kt`, `SongTextMeasurements.kt`, `SectionMotion.kt` | `songView` | |
| `ColumnCenteringHold.kt` | `songView` (keeps `ColumnCenteringHold`, `rememberColumnCenteringHold`, `FoldKeyWatch`) + `songView.grid/ColumnSpreads.kt` (`ColumnSpread`, `ColumnSpreads`, `columnKey`) | split: `SectionGrid.arrange` takes `ColumnSpreads`, so the pure grid must not import the Composable package |
| `RowSnapping.kt` | `SongRows` data class → `songView/SongRows.kt`; the rest → `screens.songDetails.stepping` | split: the layout produces `SongRows` (`SongSectionsLayout`, `onRowsPlaced`), stepping consumes it |
| `SongLyricsModel.kt` | `songView.model` (minus `rememberSongLyricsModel`) + `songView/RememberSongLyricsModel.kt` | split: the one `@Composable` leaves the pure package |
| `RenderSection.kt`, `SongRenderSections.kt`, `SongUnits.kt`, `SectionChunks.kt`, `FoldKeys.kt`, `SectionFolding.kt` (if plan 06 left it), `ChordCells.kt`, `SongMetadata.kt`, `LayoutBudget.kt` | `songView.model` | |
| `SectionGrid.kt`, `SectionCutting.kt`, `ColumnFlow.kt`, `LayoutScore.kt`, `NarrowColumns.kt`, `TextFitting.kt`, `SectionMeasurements.kt` (+ plan 07's `SongGridSearch.kt`, `SectionPlacement.kt`, `SongGridSpec.kt` if made) | `songView.grid` | |
| `SongMetadataSection.kt`, `SongPlayingControlsRow.kt`, `SongPlayingControls.kt`, `SongPlayingToolButtons.kt`, `SongPlayingTools.kt`, `SongPlayingValues.kt`, `CapoControls.kt`, `TranspositionControls.kt`, `BalancedRows.kt` | `songView.firstSection` | |
| `SongMetadataActions.kt` | `rememberSongPlayingControls` → `songView.firstSection/RememberSongPlayingControls.kt`; `rememberSongInfoEditing` → `songInfo/RememberSongInfoEditing.kt`; file deleted | split by declaration |
| `SongInfoBody.kt`, `SongInfoEditing.kt`, `SongInfoGroup.kt`, `SongInfoGroups.kt`, `SongInfoLinkButtons.kt`, `SongInfoCard.kt`, `SongDefaultsGroup.kt`, `MetadataTiles.kt` | `songInfo` (beside `SongInfo.kt`) | |
| `RowSnapFlingBehavior.kt`, `SongStepper.kt`, `PageStepper.kt`, `StepProgressIndicator.kt`, `StepTaps.kt` | `screens.songDetails.stepping` | |
| `SongDetailsScreen.kt`, `SongDetailsPager.kt`, `SongDetailsPage.kt`, `SongPages.kt`, `SongDetailsAppBar.kt`, `SongDetailsAppBarLayout.kt`, `SongDetailsActions.kt`, `SongDetailsTitle.kt`, `SongHeaderNote.kt`, `SongPagerControls.kt`, `SetlistSlots.kt`, `SongDetailsSongs.kt`, `SongKeyboardShortcuts.kt`, `SongPageDimensions.kt`, `MenuStepperRow.kt`, `FontScaleControls.kt`, `LiveFontScaleControls.kt` | stay in `screens.songDetails` | the screen itself |

Tests move with what they test:

| Test (`commonTest` unless noted) | New package |
|---|---|
| `ChordShapeCrossfadeTest`, `ColumnCenteringHoldTest`, `SongTextMeasurementsTest` (desktopTest) | `songView` |
| `RenderSectionTest`, `RenderSectionsTest`, `SongUnitsTest`, `RunFoldKeyTest`, `ChordCellsTest`, `SongMetadataTest`, `LayoutBudgetTest`, `SongModelBuildTest` | `songView.model` |
| `SectionGridTest`, `SectionGridFixtures`, `SectionCuttingTest`, `SectionPagingTest`, `LayoutScoreTest`, `NarrowColumnsTest`, `TextFittingTest`, `SectionMeasurementsTest`, `SectionSizesPoolTest`, plan 07's `FakeSectionMetrics`, `SongGridSearchTest`, `SectionPlacementTest` | `songView.grid` (`SongUnitsTest`, in `model`, imports `unitSections` from `SectionGridFixtures`) |
| `PlayingToolPlacesTest`, `BalancedRowsTest` | `songView.firstSection` |
| `SongInfoGroupsTest`, `LinkLabelTest` | `songInfo` |
| `RowSnappingTest` | `screens.songDetails.stepping` (imports `songView.SongRows`) |
| `SongDetailsTitleTest`, `SetlistSlotsTest`, `SettledViewportTest`, `PerformanceModeAppBarTest`, `SongDetailsSongsTest` | stay |

Afterwards `ui/dialogs/` and `ui/screens/songEditor/` import from `songView*` and `songInfo` only; check with
`grep -rn "ui.screens.songDetails" presentation/src --include='*.kt' | grep -v "/screens/songDetails/"`, which must list
only `ui/CampfireScreens.kt` (`SongDetailsScreen`).

### Mechanics (each commit)

- `git mv` the files, rewrite the `package` line, then add imports. **The trap:** files that shared the package used
  each other without imports; every reference that now crosses a package needs one, in both directions (the files that
  stay need imports of what left). Let the compiler list them (`:app:desktop:compileKotlin`), or generate them with a
  script over the declaration names of the moved files. KDoc links (`[SongLyrics]`) count as uses for ktlint's unused
  import rule, so a KDoc-only reference gets the import too (or becomes a backticked name where the import would be
  only for the doc).
- `./gradlew spotlessApply` for the import order.
- In `.github/scripts/size_allowances.json`, rename the key of every moved file that has one, keeping its value:
  `SectionGrid.kt` (file, `SectionGrid.arrange`, `flowIntoRows`), `SongSectionsLayout.kt` (file, `SongSectionsLayout`;
  or whatever plan 07 left), `SectionCutting.kt::flowIntoPages`, `SongChordsSection.kt::ChordRowsBlock`,
  `SongInfoBody.kt::SongInfoBody`, `SongLyrics.kt::SongLyrics`, `SongSectionContent.kt::SongSectionContent`. Then
  `python3 .github/scripts/check_sizes.py` must pass; run `--update` only where a commit shrank something (the
  CLAUDE.md).
- Check each move mechanically: `git diff -M --color-moved=dimmed-zebra HEAD~1` shows only package lines and imports
  as changed, and the sorted list of top-level declarations of the module is the same before and after.

### Commits (each compiles and passes the tests)

1. `songInfo`: the eight `SongInfo*`/`SongDefaultsGroup`/`MetadataTiles` files and `rememberSongInfoEditing` (out of
   `SongMetadataActions.kt`), their tests, the four importers outside the package (`SongInfoSheet`, `SongLinksDialog`, `LoadedSongEditor`, and `SongPreview` for
   `SongInfoEditing`); new `ui/songInfo/CLAUDE.md`.
2. `songView.grid`: the grid files, `ColumnSpreads.kt` split out of `ColumnCenteringHold.kt`, their tests; new
   `ui/songView/grid/CLAUDE.md`.
3. `songView.model`: the model files, `SongLyricsModel.kt` minus `rememberSongLyricsModel` and the `private class
   InPlaceSongLyricsModel` only it uses (both stay in
   `screens.songDetails` for this one commit, in `RememberSongLyricsModel.kt`), their tests, and `SongPreview.kt`'s
   imports of `ChordDiagrams` (in `ChordCells.kt`), `SongLyricsInputs` and `prepareSongLyrics`; new
   `ui/songView/model/CLAUDE.md`.
4. `songView.firstSection`: its files and `rememberSongPlayingControls` (deleting the now empty
   `SongMetadataActions.kt`), their tests; new `ui/songView/firstSection/CLAUDE.md`.
5. `songView`: the renderer files, `RememberSongLyricsModel.kt`, `SongRows.kt` (out of `RowSnapping.kt`), the rest of
   `ColumnCenteringHold.kt`, their tests, `SongPreview.kt`'s imports; new `ui/songView/CLAUDE.md`.
6. `screens.songDetails.stepping`: the stepping files and `RowSnappingTest`; new
   `ui/screens/songDetails/stepping/CLAUDE.md`; `ui/fontScale/CLAUDE.md`; the rest of the CLAUDE.md trim and pointer
   updates below, and `check_sizes.py --update` for the shrunk `screens/songDetails/CLAUDE.md`.

Commit messages in the repo's voice, e.g. `Move the About the song body into ui/songInfo.`

### CLAUDE.md split (from `ui/screens/songDetails/CLAUDE.md`, 4,535 counted words)

Each new file ≤ 600 words (`check_sizes.py` counts after the license header). The sections add up to more than the
budgets, so each is **cut while moving, not copied**: drop narration of what the code plainly does, keep the rules and
the one-sentence whys. Headers name the package (`# :presentation — ui/songView/grid`).

| Section today (words) | Goes to | Target words |
|---|---|---|
| Intro + "The songs it pages through" (122) | stays in `screens/songDetails/CLAUDE.md`; the intro gains one line pointing at `ui/songView/` for drawing and layout and `stepping/` for the pedal | ~130 |
| "Rendering" (341): monospace, direction, chords drawn not composed, content description, `TextMeasurer`/`SongTextMeasurements` bullets | `songView/CLAUDE.md` | ~170 |
| "Rendering": cut sections joined, key change, lyrics-only mode, comment chords | `songView/model/CLAUDE.md` (point at `ui/songLayout/` for `joinCutSections` once plan 06 moved it there) | ~150 |
| "Tabs and grids" (183) | `songView/CLAUDE.md` | ~160 |
| "Tempo and time changes" (166) | `songView/model/CLAUDE.md`, except the `searchSegment`/`concatenated` bullet → `grid` | ~120 + ~30 |
| "Folding" (361): keys, saved per song, numbering, what the editor's preview folds | `songView/model/CLAUDE.md` (the keying rules) and `songView/CLAUDE.md` (pill, chevron, chorus title row, re-measure only own section) | ~180 + ~120 |
| "The first section" (253) | `songView/firstSection/CLAUDE.md` | ~250 |
| "The About the song sheet and its editors" (367) | `songInfo/CLAUDE.md` (plan 12 may later move the `SongMetadataDialog` / `SongLinksDialog` bullets next to those dialogs) | ~360 |
| "Building the model, and the budget" (142) | `songView/model/CLAUDE.md` | ~140 |
| "Layout" (971): search, widths, even fill, spread, rows like systems, `flowIntoRows`, wide rows, magazine, narrow columns, text fitting, single-column paging, chunks | `songView/grid/CLAUDE.md` | ≤ 600 (cut the DP narration hardest) |
| "Layout": hold (`ColumnCenteringHold`), continuation piece header, chorus cards behind chunks, Motion, `animateBounds` limit, traversal groups, `availableHeight`, preview/songbook single column | `songView/CLAUDE.md` | ~200 |
| "Scrolling, stepping, and snapping" (581) + "Keeping the reader in place" (133) | `screens/songDetails/stepping/CLAUDE.md`, except the `minRowPitch` / `minLastRowHeight` bullet → `songView/CLAUDE.md` (the layout pads the rows) | ≤ 600 |
| "Text size and the steppers": `fontScale` state, settled scale, whole percent, gestures (≈200) | new `ui/fontScale/CLAUDE.md` (that package holds `fontScaleGestures` and the accumulator) | ~200 |
| "Text size": stepper height, app bar placement (≈130); transposition wraps (≈25) | stay in `screens/songDetails/CLAUDE.md`; the wrap bullet → `ui/playing/CLAUDE.md` (461 words, room for it) | |
| "The app bar", "Keeping the display on", "Keyboard", "The metronome button" (501) | stay in `screens/songDetails/CLAUDE.md` | ~480 |

`songView/CLAUDE.md` must come out ≤ 600 with ~650 of input: cut. The remaining `screens/songDetails/CLAUDE.md` is about
750 words, still over the package budget but on its (shrinking) allowance; `--update` lowers it. Trim further if it is
easy (the metronome button section repeats `ui/metronome/CLAUDE.md`, which already owns the pendulum; leave a one-line
pointer). No new package gets a CLAUDE.md beyond those listed: `screens.songDetails` root files are described by the
remaining one.

Pointers to update afterwards (grep `songDetails` in every `CLAUDE.md`, `.claude/skills/`, `documentation/`):
`ui/chords/CLAUDE.md` (`screens/songDetails/SongChordsSection.kt` → `songView/SongChordsSection.kt`),
`ui/metronome/CLAUDE.md` (two references; "layout in `screens/songDetails/CLAUDE.md`" → `songView/model/CLAUDE.md`, the
tempo-change rules' new home), `documentation/plans/pedal-coverage-test.md` (its test file names stay; check its paths).
`data/source/remote/implementation/CLAUDE.md`'s `songDetails/SongDetailsCandidates.kt` is another module's package:
leave it.

## Tests

None new: a pure move. Every existing test moves with its code (tables above) and must pass unchanged apart from its
`package` line and imports. Verify with the root CLAUDE.md test command (`:presentation:desktopTest` is the one that
matters) plus `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, `python3 .github/scripts/check_sizes.py`,
`python3 .github/scripts/check_license_headers.py` (new CLAUDE.md files carry the header) and `./gradlew spotlessCheck`.
