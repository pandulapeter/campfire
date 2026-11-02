# Enforce one-way package layers inside `:presentation` with a pre-commit import check, after moving the upward imports down

**Kind:** structure · **Severity:** medium · **Priority:** P1 · **Platforms:** all · **Lane:** P (first) · **Effort:** M
**Files:** `presentation/src/*/kotlin/com/pandulapeter/campfire/presentation/**` (moves listed below, their tests in
`presentation/src/commonTest` and `desktopTest`), `.github/scripts/check_layers.py` (new),
`.github/scripts/test_check_layers.py` (new), `.githooks/pre-commit`, `.github/workflows/tests.yml`,
`.claude/settings.json`, `presentation/CLAUDE.md`, `.claude/skills/code-style/SKILL.md` ("Files and packages"), every
package `CLAUDE.md` naming a moved file, `tools/screenshots/src/test/**` (imports of moved declarations)

**Challenged:** amended — the root package (`Module.kt`, the generated `CAMPFIRE_VERSION_NAME`) is foundation, not
surfaces (else `ui.firstRun` and `ui.state.Breadcrumbs` fail with no move listed); the script knows the table's
packages as well as the source's (`resources` is generated and has no `package` line); step 1 also deletes the KDoc-only
and dead imports that the step 3 moves would otherwise carry into the app layer. Table re-measured at `adfc215ae`: 46
package edges, 109 import lines, 44 files, every one covered by a step below.

`ui/` below is `presentation/src/<sourceSet>/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

The code-style skill already says a screen's package holds only that screen, shared blocks live in shared packages, and
only a screen receives the view model. Nothing checks it, so at `76f3a2c9b` 51 of the 54 package groups of
`:presentation` form one strongly connected import component: `components` imports `navigation`, `chrome`, `update`,
`metronome`, `rendering` and `CampfireViewModel`; `platform` imports `sync`; `settingsPage` imports
`screens.settings`; and the state holders import their models out of screen packages (`ui.state.importing` imports
`screens.importReport.ImportReport`, `ui.navigation` imports `screens.onboarding.OnboardingStep`,
`screens.setlists.SetlistWithSongs`, `screens.chords.ChordLibraryState`, `ui.state.AppExitController` imports
`screens.songEditor.EditorSession`). Any package can reach any other, so no part of the UI can be read, tested, or
later split into a module on its own, and an agent adding an import has no signal that it went the wrong way.

Measured with the table below on main sources (tests excluded): 45 package edges, about 110 import lines, in about 40
files. The reviewers' "~15 upward imports" counted only the edges that break the cycle at the coarsest cut; a rule an
agent can follow needs the full list moved.

## Fix

### The layers

Four layers, lowest first; a file may import its own layer and lower ones only. A package belongs to the longest prefix
listed; every `ui.*` package not listed is **app**.

| Layer | Packages |
|---|---|
| foundation | the `com.pandulapeter.campfire.presentation` package itself, matched exactly, never as a prefix (`Module.kt`, the generated `CampfireVersion.kt`), `localization`, `mapper`, `resources` (generated), `ui.theme`, `ui.platform`, `ui.search` |
| components | `ui.components`, `ui.settingsPage` |
| app (default) | everything else under `ui.`: `state`, `navigation`, `dialogs` (the package itself: `DialogHost`, `DialogType`), `dialogs.sheet`, `metronome`, `tuner`, `playing`, `chords`, `scales`, `songView`, `songLayout`, `print`, `sync`, `share`, … |
| surfaces | `ui.screens`, `ui.chrome`, every `ui.dialogs.<sub>` except `ui.dialogs.sheet`, the `ui` package itself (exactly, as the root above) |

Two more rules: a file under `ui.screens.<a>` imports nothing under `ui.screens.<b>` for `b ≠ a` (the `ui.screens`
package itself, which holds the handlers several screens share, is allowed); and, **switched off until plan 14's last
step**, `ui.CampfireViewModel` is imported only by files in the `ui` package (which need no import) and by a destination's
entry file, a file named `*Screen.kt` or `ExportHost.kt` directly in a `ui.screens.<a>` package. While the view model
rule is off, an import of `ui.CampfireViewModel` is judged by nothing (it would otherwise fail the layer rule in about
20 app-layer files — `metronome`, `songView.firstSection`, `songInfo`, `navigation`, `messages`, `fontScale`, `update`,
`sync`, `launch`, `state.importing`, `dialogs` — that plan 14 converts).

A finer cut (a "music" layer for `chords`, `scales`, `tabs`, `instrument` below the app state) was measured: about ten
more moves (`tuner`'s `noteName`/`tuningLabel`, `songView`'s `songControlHeight`/`CapoControls`, `playing`'s
`EffectiveCapo`) for a cycle among packages that already sit in one layer. Not worth it now; the table can be refined
later in a change of its own.

### Steps (one commit each; every platform compiles and `desktopTest` passes after each)

Moves are verbatim (code-style "A behaviour-preserving split moves code verbatim"): same declarations and KDoc,
`private` widened only where needed, tests moved into the new package with their code, then
`grep -rn '<OldPackage>.<Name>\|<old/path>' .` over the whole repository for imports, KDoc, `CLAUDE.md` and skills.
Check each move with the sorted declaration list before and after (`git diff --color-moved=dimmed-zebra`). Write step
5's script first and keep it uncommitted until step 5, running it over the whole tree after each step to watch the list
shrink to nothing.

1. **Dead and documentation-only imports.** Delete the unused `import …dialogs.forms.label` in
   `ui/dialogs/sheet/SheetHeader.kt`, `ui/dialogs/DeleteLibraryDialog.kt` and `ui/dialogs/ImportProgressContent.kt`
   (the last defines its own private `ImportProgress.Phase.label`). Where an import exists only for a KDoc link to a
   higher layer — `CampfireViewModel` in `ui/components/labels/LabelsOnEverySong.kt`; `dialogs.pickers.SetlistPicker` /
   `SongPicker` (the composables, not `DialogType.SetlistPicker`) in `ui/dialogs/DialogHost.kt`, `DialogType.kt`,
   `SavedDialog.kt`, `SavedDialogRestorer.kt`; `dialogs.instrument.ChordShapeGrid` in `ui/dialogs/ChordShapeSearch.kt`
   — drop the import and write the name in backticks instead of a link. The same for the files step 3 moves, whose
   imports would otherwise cross from the app layer: in `screens/importReport/review/ImportReviewSections.kt` the
   KDoc-only `finished.ImportReportSection` and `finished.matching` and the unused `finished.sections`; the unused
   `ui.CampfireViewModel` in `screens/importReport/ImportReport.kt`, `screens/settings/DemoLibraryOffer.kt`,
   `screens/songEditor/EditorTextEdit.kt`, `dialogs/lookup/CoverArtSearchState.kt` and `SongDetailsLookupState.kt`.
   Confirm each by deleting the import and compiling (`:app:desktop:compileKotlin`).
2. **Foundation and components stop reaching up.**
   - `ui/chrome/ToolPagePadding.kt` (`TOOL_PAGE_PADDING`, `TOOL_GROUP_GAP`) → `ui/components/` (used by
     `components/PinnedSearchField.kt` and `tuner/TunerStringsSetting.kt`).
   - `ui/components/ScrollToTopOnRequest.kt` → `ui/navigation/` (its only users are the four tool screens; it takes
     `CampfireDestination` and `ScrollToTopRequest`).
   - `LocalIsCoveredByRequiredUpdate` (declared in `ui/update/AppUpdateGate.kt`) → its own file in
     `ui/components/menu/` (read by `OverflowMenu.kt`, provided by `AppUpdateGate`).
   - `Song?.timeSignatureOrDefault` (`ui/metronome/MetronomePatterns.kt`) and `timeSignatureOf`
     (`ui/metronome/ChordProTimeSignature.kt`, with `ChordProTimeSignatureTest`) → `mapper/` (both map a song's text to a
     `TimeSignature`; `components/songCard/SongListItem.kt` and `songView/model` read them).
   - `rememberSongCardKey` (`ui/components/songCard/SongCardOptions.kt`, the only use of `SongRenderer` there) →
     `ui/rendering/`.
   - `ui/settingsPage/PageWidthLayout.kt` and `SettingsPage.kt` import `SettingsTabPage` and `SettingsCategoryPane`
     from `screens.settings` for KDoc links only (step 1 treatment) and `SETTINGS_CATEGORY_PANE_WIDTH` for real: move
     that constant from `screens/settings/SettingsCategoryPane.kt` into `ui/settingsPage/` (the screen imports it
     down).
   - `ui/platform/SyncConsentOrigin*.kt` (the expect in `commonMain`, an actual in each of `androidMain`, `desktopMain`,
     `iosMain`, `wasmJsMain`) → package `ui.sync`, next to `SyncConsentOrigin` (`ui/sync/SyncConsent.kt`).
3. **State out of screen and dialog packages** (each line a group of files moved together, tests with them):
   - `screens/importReport/ImportReport.kt` (with `ImportFailure`, `isUnfinished`, `followingLibraryFileNames`) and
     `screens/importReport/review/ImportReviewSections.kt` (review sections, summary, `replaceCount`,
     `hasEntriesToChoose`, `withGroupResolution`) → `ui/state/importing/`. Fixes `state.importing`, `navigation`,
     `firstRun`, `dialogs`, `dialogs.labels` and `screens.songDetails` importing `screens.importReport`.
   - `screens/songs/SongGroups.kt`, `SongGroup.kt`, `SongGroupHeader.kt` → `ui/state/songs/`.
   - `screens/setlists/ListedSetlists.kt`, `SetlistWithSongs.kt`, `SetlistDetailsEdit.kt` (`SetlistDetails`, `details`,
     `mergedSetlistDetails`) → `ui/state/setlists/`.
   - `screens/settings/LibrarySummary.kt` → `ui/state/`; `screens/settings/DemoLibraryOffer.kt` → `ui/firstRun/`.
   - `screens/onboarding/OnboardingStep.kt` (`OnboardingStep`, `onboardingSteps`, `nextOnboardingStep`,
     `previousOnboardingStep`) → `ui/navigation/`.
   - `screens/chords/ChordLibraryState.kt` → `ui/chords/`; `screens/scales/ScaleLibraryState.kt` → `ui/scales/`.
   - `shareTargetOf` (`screens/export/ExportFormats.kt`) → `ui/share/`, with whatever of that file it needs.
   - `screens/songEditor/EditorSession.kt` and `EditorTextEdit.kt` → new `ui/state/editor/`, with the same-package
     declarations it then fails to compile without (expected: `EditorFieldSaver` from `EditorField.kt`,
     `placeholderTitleSelection` from `PlaceholderTitleSelection.kt`); its `CLAUDE.md` section moves with it.
   - `dialogs/lookup/CoverArtSearchState.kt` and `SongDetailsLookupState.kt` (with `SongCreditsLookup`) →
     `ui/state/online/`.
   - `dialogs/forms/SongMetadataFields.kt` (`Field.label`, `SONG_METADATA_FIELDS`) and `dialogs/forms/DurationDigits.kt`
     (`toSongMetadataDraft`, `durationDigitsOf`, `durationTextOf`) → `ui/songInfo/`.
4. **Surfaces that sit in the app layer.**
   - `ui/dialogs/CampfireDialogs.kt` (the dispatcher naming every sheet) → the `ui` package, next to `CampfireScreens.kt`.
   - `ui/songInfo/SongInfoSheet.kt` (imports `dialogs.forms.SongPlayingDialog`) → `ui/dialogs/forms/`.
   - `ui/dialogs/SongFiltersSheet.kt` (imports `screens.SongFilterHandlers`) → `ui/screens/` beside the handlers, or
     the handlers' two functions into `ui/components/songFilters/`; pick whichever leaves fewer imports.
5. **The check.** `.github/scripts/check_layers.py`, beside `check_sizes.py` and written like it:
   - Knows the packages from every `package` line under `presentation/src/*/kotlin` plus the table's own entries
     (`resources` is generated and declares none), then for each judged file every
     `import com.pandulapeter.campfire.presentation.…` line, resolved to the longest known package that prefixes it (a
     top-level function's import names it after the package). Main source sets only: tests import shared helpers from
     wherever they sit (`ui/UseCaseStubs.kt` in the `ui` package, `ui.playing.testPreferences`), which is no layering
     of the app.
   - The table above as a short literal in the script, plus the screen rule and the view model rule; the latter behind
     `VIEW_MODEL_RULE = False` with a comment naming plan 14.
   - Modes as `check_sizes.py`: no arguments judges the staged `.kt` files of `presentation/` (`.githooks/pre-commit`);
     `--base <rev>` judges the files changed since it (`tests.yml`, next to the size check); `--hook` judges the file a
     Claude Code edit wrote (`.claude/settings.json`, next to `check_sizes.py --hook`). A deleted file is judged by
     nothing. Since HEAD is clean after step 4, judging every import of a touched file needs no "crossing" logic, no
     baseline and no exception list.
   - A failure names the file, the import, both layers, and the one-line rule ("components may not import app; move the
     declaration down or pass it in").
   - `test_check_layers.py` beside it (`unittest`, run by `tests.yml`'s `discover`): an allowed import, a layer
     violation, a screen-to-screen import, the `ui.screens` package allowed, the view model rule on and off, a top-level
     function import resolved to its package, a test source file and a file outside `presentation/` ignored.
   - Docs: the table and the two rules in `presentation/CLAUDE.md` (short, pointing at the script); the code-style
     skill's "Package by role" bullet names the script; the root `CLAUDE.md` pre-commit paragraph lists it.

## Tests

`test_check_layers.py` as above. The moves are covered by the existing tests moving with their code (run
`./gradlew desktopTest :tools:screenshots:test` once after step 4) and by the check passing on the whole tree
(`python3 .github/scripts/check_layers.py --base $(git rev-list --max-parents=0 HEAD)`) before step 5 is committed.
