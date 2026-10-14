<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Give `ui/components` and `ui/dialogs` subpackages by role, and move single-feature pieces into their features

**Challenged:** amended — `SortableChipRow` goes to `pickers` (its only user is `SongPicker`, not a labels sheet); plan
02's `SavedDialogRestorer` and its test are placed at the dialogs root; `ChordsIcon`'s "only user" claim corrected; the
`SetlistSongToggleTest` split copies the shared fixtures; plans 08 and 09 (which edit dialog files and
`dialogs/CLAUDE.md`) must land first.

**Kind:** structure · **Severity:** medium · **Platforms:** all (commonMain only)
**Files:** every file in `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/`
and `.../ui/dialogs/` with their tests in `commonTest/.../ui/components/`, `commonTest/.../ui/dialogs/` and
`desktopTest/.../ui/dialogs/OpenSourceComponentsTest.kt`; `ui/screens/setlists/SetlistSongToggle.kt` and
`commonTest/.../ui/screens/setlists/SetlistSongToggleTest.kt`; every importer of a moved declaration (the compiler
lists them); CLAUDE.md files `ui/components/CLAUDE.md`, `ui/dialogs/CLAUDE.md` and the new ones below, `ui/CLAUDE.md`,
`ui/screens/CLAUDE.md`, `ui/tuner/CLAUDE.md`, `ui/chords/CLAUDE.md`, `ui/screens/songDetails/CLAUDE.md`, root
`CLAUDE.md`, `.claude/skills/code-style/SKILL.md`; `.github/scripts/size_allowances.json`.

(`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

## Problem

`ui/components/` is 116 files and `ui/dialogs/` 67, each flat, with CLAUDE.md files of 2,429 and 2,339 counted words
on allowances (budget 600). A reader cannot tell the song card's files from the search bar's from the filter panel's,
and the docs cannot follow packages. Several "components" have exactly one user (`DragHandle`, `MissingSongListItem`:
setlists; `CardDetailsChoice`, `LinkListItem`: settings; `DismissSheetWhenSidePanelAppears`: songs; `InstrumentList`:
dialogs). `ui/screens/setlists/SetlistSongToggle.kt` mixes the setlist writes `state/SetlistsController.kt` imports
(`withSongTicked`, `withSongsAdded`, `withSongsTakenOut`) with the box logic only `dialogs/SelectionSetlistsSheet.kt`
imports (`heldOf`, `withHeldRecorded`, `selectionBoxState`), so state and a dialog both reach into a screen package.
`components/TextResource.kt` is string reading, which belongs with `localization/`.

## Fix

Moves only, behaviour unchanged, everything stays `internal`. **Order:** after plans 02, 08 and 09 too, which edit files and CLAUDE.md sections this plan moves or splits (02 adds
`SavedDialogRestorer`; 08 the `toChordNotation` import in `ChordReferenceSheet`, `ChordShapeSheet`, `TunerSheet`,
`TuningSheet` and a `dialogs/CLAUDE.md` sentence; 09 the song picker bullet, `components/TagFilters.kt`, `components/SongLabels.kt` and `dialogs/SongPicker.kt`); and
after plan 11 (which creates the `ui/songInfo`
package `SongInfoSheet` joins and moves most importers of these files), before plan 13 (which takes `NavigationItemPresence` and puts `ContentOverscroll` into
`components`).

### `ui/components` target layout

| Package (`ui.components.`) | Files |
|---|---|
| `songCard` | `SongListItem`, `CenteredSongCardContent`, `SongCardDefaults`, `SongCardOptions`, `ListItemHeadline` |
| `list` | `ListLayout`, `ListColumns`, `ListPlaceholder`, `ListTopFade`, `ListUnderAppBar`, `ListItemAnimation`, `ListItemMetrics`, `FastScroller`, `FastScrollerDetent`, `SectionHeader`, `SectionHeaderState`, `ScrollToTopWhenChanged`, `ContextClick` |
| `appBar` | `SearchableTopAppBar`, `SearchBarMotion`, `SearchToCloseIcon`, `SearchFieldSlot`, `AppBarOverlap`, `CampfireTopAppBar` |
| `menu` | `ActionsMenu`, `ActionOverlap`, `OverflowMenu`, `OverlayState`, `SortMenu`, `SongSortMenu`, `SetlistSortMenu`, `NewItemMenu` |
| `actions` | `SongActions`, `SongActionHandler`, `SetlistActions`, `SetlistActionHandler`, `SetlistAssignmentsButton` |
| `songFilters` | `SongFilters`, `SongFilterActions`, `SongFilterUiState`, `FilterGroupChips`, `FilterGroupsLayout`, `FilterSectionTitle`, `CollapsibleChipFlow`, `TagFilters`, `LanguageFilters`, `MatchModeChoice`, `CountedFilterChip`, `ControlsSidePanel` |
| `checklist` | `CheckboxListItem`, `ChecklistOrder`, `KeepChecklistRowsInPlace` |
| `labels` | `TagPill`, `TagIcon`, `TagColor`, `LocalTagColors`, `TagFlowRow`, `SongLabels`, `Languages`, `LabelOrder`, `LabelSortingToggle`, `LabelsOnEverySong` |
| `controls` | `Stepper`, `SegmentedChoice`, `SelectableChip`, `PickerChip`, `LabeledControlRow`, `ControlsMetrics`, `SongToolButton`, `ExpandChevron`, `PagerIndicator`, `ColorChoice` |
| `preferences` | `ThemeColorChoice`, `UiModeChoice`, `BackgroundWarmthSlider`, `LanguageChoice`, `AppLanguageNames` |
| `coverArt` | `CoverArt`, `CoverArtImage`, `ProvideCoverArtImageLoader`, `AppBarCover` |
| root (generic) | `EdgeFade`, `PaddingValuesSides`, `WindowSize`, `ScaledTextStyle`, `KeySeparator`, `RelativeTime`, `Contrast`, `DelayedLoadingIndicator`, `EmptyState`, `Placeholder`, `SaveShortcut`, `ImportProgress`, `ClearTextButton`, `SearchField`, `SearchState`, `ScrollPosition`, `ScrollToTopOnRequest`, `ScrollToStartWhenChanged`, `HideKeyboardWhenScrolledDown`, `ActionListItem`, `RadioListItem`, `SwitchListItem`, `NavigationItemPresence` (plan 13 moves it on) |

Out of `components` into the feature that is their only user:

| File | To |
|---|---|
| `DragHandle.kt`, `MissingSongListItem.kt` | `ui.screens.setlists` |
| `CardDetailsChoice.kt`, `LinkListItem.kt` | `ui.screens.settings` |
| `DismissSheetWhenSidePanelAppears.kt` | `ui.screens.songs` |
| `InstrumentChips.kt`, `InstrumentChipRow.kt`, `InstrumentSetting.kt` | `ui.instrument` (beside `Instruments.kt`, `InstrumentLabels.kt`; users are settings, tuner, chords, onboarding, song details) |
| `ChordsIcon.kt` | `ui.chords` (by subject, not by user: `DestinationIcon` and onboarding's `FeaturesStep` draw it, and keep importing it) |
| `TextResource.kt` | `presentation.localization` (beside `ApplyLanguagePreference.kt`) |
| `InstrumentList.kt` | `ui.dialogs.instrument` |

Re-check each "only user" claim before moving (`grep -rlw <Name> presentation/src`); one that gained a second user stays
in its components subpackage. `SearchState` and `SearchField` stay at the root rather than in `appBar` because
`state/`, `navigation/`, the dialogs and the Chords tab use them without any bar.

### `ui/dialogs` target layout

| Package (`ui.dialogs.`) | Files |
|---|---|
| root (the stack) | `CampfireDialogs`, `DialogHost`, `DialogStack`, `DialogType`, `SavedDialog`, `SongEditTarget`, `ConfirmationDialog`, `SingleConfirmation`, `UnsavedChangesDialog`, `DeleteLibraryDialog`, `ImportProgressContent`, `SongLabel`, plan 02's `SavedDialogRestorer` |
| `sheet` | `CampfireBottomSheet`, `BottomSheetConfirmButton`, `BottomSheetContentScope`, `SheetContentHeight`, `SheetHeader`, `SheetHeightAnimation`, `SheetSaveShortcut`, `TextFieldBottomSheet`, `FirstFieldFocusRequester`, `DialogChecklist`, `SingleLineText` |
| `forms` | `NewSongDialog`, `SongMetadataDialog`, `SongMetadataField`, `SongMetadataFields`, `SongMetadataShortFields`, `DurationDigits`, `SongLinksDialog`, `SetlistDetailsDialog`, `SongPlayingDialog`, `TitleLength` |
| `labels` | `TagsSheet`, `LanguagesSheet`, `BulkLabelDraft`, `TagColorButton`, `SongTagsDialog`, `SongLanguagesDialog`, `SelectionTagsSheet`, `SelectionLanguagesSheet` |
| `pickers` | `SetlistPicker`, `SetlistPickerContents`, `SongPicker`, `PickerList`, `SelectionSetlistsSheet`, `AppLanguageSheet`, new `SelectionSetlistBoxes.kt` (below), `SortableChipRow` (from components; `SongPicker` is its only user) |
| `lookup` | `SongDetailsLookupSheet`, `SongDetailsLookupState`, `SongDetailsCandidateRow`, `SongDetailsReview`, `CatalogueQueryFields`, `CoverArtSearchSheet`, `CoverArtSearchState`, `CoverArtResults`, `CoverArtAddress` |
| `instrument` | `InstrumentSheet`, `TuningSheet`, `NotationSheet`, `ChordShapeSheet`, `ChordShapeGrid`, `ChordReferenceSheet`, `TunerSheet`, `InstrumentList` |
| `about` | `OpenSourceComponents`, `OpenSourceLicensesSheet`, `WhatsNewDialog` |
| `ui.songInfo` (out of dialogs) | `SongInfoSheet` (its body, editing state and CLAUDE.md section are there after plan 11) |

`SongLabel.kt` (`songLabel`) stays at the root: the sheet header, many dialogs and the export screen use it.
`DeleteLibraryDialog` is a typed confirmation, kept at the root with the other confirmations rather than in `forms`.

### `SetlistSongToggle.kt` split

- `Setlist.withSongTicked`, `withSongsAdded`, `withSongsTakenOut` (only `state/SetlistsController.kt` uses them) →
  `ui/state/SetlistEntryEdits.kt` (plan 13 then moves it to `state/setlists/` with the controller).
- `heldOf`, `withHeldRecorded`, `selectionBoxState` (only `SelectionSetlistsSheet` uses them) →
  `ui/dialogs/pickers/SelectionSetlistBoxes.kt`.
- Split `SetlistSongToggleTest` the same way: its `withSongTicked` / `withSongsAdded` / `withSongsTakenOut` cases into
  `commonTest/.../ui/state/SetlistEntryEditsTest.kt`, its `heldOf` / `selectionBoxState` cases into
  `commonTest/.../ui/dialogs/pickers/SelectionSetlistBoxesTest.kt`; delete the old file. Case bodies unchanged; the class's `private val setlist` fixture goes into both new classes, and
  `selection` / `setlistOf` into the boxes test, which alone uses them.

### Tests move with their code

| Test | To |
|---|---|
| components: `SongCardOptionsTest` | `songCard` |
| `ListLayoutTest`, `ListTopFadeTest`, `FastScrollerTest`, `FastScrollerDetentTest` | `list` |
| `OverlayStateTest` | `menu` |
| `CollapsibleChipFlowTest` | `songFilters` |
| `ChecklistOrderTest` | `checklist` |
| `TagColorTest`, `LabelOrderTest`, `LabelsOnEverySongTest` | `labels` |
| `LabeledControlRowTest`, `PagerIndicatorTest`, `SegmentedChoiceTest` | `controls` |
| `SearchStateTest`, `RelativeTimeTest`, `ContrastTest` | stay at the components root |
| dialogs: `DialogHostTest`, `DialogStackTest`, `SavedDialogTest`, `ImportProgressContentTest`, plan 02's `SavedDialogRestorerTest` | stay at the dialogs root |
| `DurationDigitsTest`, `SongLinksDraftTest`, `SongPlayingDraftTest` | `forms` |
| `BulkLabelDraftTest` | `labels` |
| `SetlistPickerContentsTest` | `pickers` |
| `SongDetailsReviewTest` | `lookup` |
| `ChordShapeGridTest` | `instrument` |
| `OpenSourceComponentsTest` (desktopTest) | `about` |

### Mechanics

- `git mv`, rewrite the `package` line, add the imports that same-package use never needed (in both directions: the
  files left at a root now import what moved). Let `:app:desktop:compileKotlin` list them; KDoc links need the import
  too, as ktlint counts them. `./gradlew spotlessApply`.
- `size_allowances.json`: rename, keeping the value, the keys of moved files:
  `components/FastScroller.kt::FastScroller` → `components/list/...`, `components/SearchableTopAppBar.kt` (file and
  function) → `components/appBar/...`, `components/SectionHeader.kt::SectionHeader` → `list`,
  `components/SongListItem.kt::SongListItem` → `songCard`, `dialogs/CampfireBottomSheet.kt::CampfireBottomSheet` →
  `sheet`, `dialogs/CoverArtResults.kt::CoverArtResults` and `dialogs/CoverArtSearchSheet.kt::CoverArtSearchSheet` and
  `dialogs/SongDetailsLookupSheet.kt::SongDetailsLookupSheet` → `lookup`, `dialogs/LanguagesSheet.kt::LanguagesSheet`
  and `dialogs/TagsSheet.kt::TagsSheet` → `labels`. `CampfireDialogs.kt::CampfireDialog` stays. Run
  `python3 .github/scripts/check_sizes.py`; `--update` after the CLAUDE.md files shrink.
- Check each commit with `git diff -M --color-moved=dimmed-zebra` (only package lines and imports change) and grep the
  repository for each old path afterwards.

### Commits (each compiles and passes the tests)

One per target package: `components` songCard, list, appBar, menu, actions, songFilters, checklist, labels, controls,
preferences, coverArt; the single-user moves out of `components` (one commit, including `TextResource`); `dialogs`
sheet, forms, labels, pickers (with the `SetlistSongToggle` split), lookup, instrument, about, `SongInfoSheet` →
`songInfo`. Each carries its CLAUDE.md piece below, so no commit leaves a doc naming a path that moved.

### CLAUDE.md split (each new file ≤ 600 counted words; cut narration while moving)

From `ui/components/CLAUDE.md`:

| Section | To |
|---|---|
| Song cards (261) | `components/songCard/CLAUDE.md` (the `DragHandle` bullet stays here: it is about where the card's overflow button lands; `hasRoomForSidePanel` → `songFilters`) |
| `SearchableTopAppBar` (479) + `CampfireTopAppBar` (63) | `components/appBar/CLAUDE.md` (~540) |
| Menus and actions (317): `ActionsMenu`, always-in-menu, every overflow is a dropdown, `OverflowMenu` counting, `SortMenu` | `components/menu/CLAUDE.md` (~200) |
| Menus and actions: `SongActions`, Choose setlists button, `SetlistActions` | `components/actions/CLAUDE.md` (~120) |
| Menus and actions: `SongFilters` / `ControlsSidePanel` bullet; `FilterGroupsLayout` (152); the `CountedFilterChip` bullet of TagFlowRow | `components/songFilters/CLAUDE.md` (~250) |
| `TagFlowRow` / `TagPill` (rest, ~170) + `Languages.kt` (62) | `components/labels/CLAUDE.md` (~230) |
| Other choices (81) + `LabeledControlRow` (51) + `PagerIndicator` (22) | `components/controls/CLAUDE.md` (~155) |
| `FastScroller` (180) + Sticky section headers (87) + the `ListTopFade` and `FastScroller` bullets of Scrolling performance | `components/list/CLAUDE.md` (~330) |
| `CoverArtImage` / `ProvideCoverArtImageLoader` (118) | `components/coverArt/CLAUDE.md` |
| `ChordsIcon.kt` (13) | dropped: it narrates the code, and `ui/chords/CLAUDE.md` has no allowance left to take it |
| Edge fades, `SaveShortcut`, `DelayedLoadingIndicator`, the overscroll bullet of Scrolling performance | stay in `components/CLAUDE.md` (~250; plan 13 points the overscroll bullet at the moved `ContentOverscroll`) |

From `ui/dialogs/CLAUDE.md`:

| Section | To |
|---|---|
| Intro (122) + The dialog stack (258) + `CampfireDialogs.kt` and the rest minus What's new (~90) | stay in `dialogs/CLAUDE.md` (~470) |
| `CampfireBottomSheet` (407) | `dialogs/sheet/CLAUDE.md` |
| Forms (298) + Song defaults sheet (65) | `dialogs/forms/CLAUDE.md` (~360; may also take the `SongMetadataDialog` / `SongLinksDialog` bullets from `ui/songInfo/CLAUDE.md` if both stay ≤ 600) |
| Pickers (321) minus the language picker bullet, + the selection sheets' intro and Choose setlists bullet | `dialogs/pickers/CLAUDE.md` (~420) |
| Manage tags / languages and tag color bullets + the language picker bullet | `dialogs/labels/CLAUDE.md` (~170) |
| `CoverArtSearchSheet.kt` (167) + `SongDetailsLookupSheet.kt` (157) | `dialogs/lookup/CLAUDE.md` (~325) |
| `OpenSourceLicensesSheet.kt` (146) + What's new bullet (~70) | `dialogs/about/CLAUDE.md` (~215) |

`preferences`, `checklist`, `dialogs/instrument` get no CLAUDE.md: nothing non-obvious to say. Both residual files lose
their allowances (they end under 600); `check_sizes.py --update` drops them.

Pointers to update (then grep every `CLAUDE.md`, `.claude/skills/`, `README.md` for each old path):
- root `CLAUDE.md` Build section: `ui/dialogs/OpenSourceComponents.kt` → `ui/dialogs/about/OpenSourceComponents.kt`
  (the root's `components/EdgeFade.kt` stays valid).
- `.claude/skills/code-style/SKILL.md`: `components/TextResource.kt` → `localization/TextResource.kt`.
- `ui/CLAUDE.md`: `dialogs/TagColorButton.kt` → `dialogs/labels/`; `components/InstrumentChips.kt` →
  `ui/instrument/InstrumentChips.kt`; "see `components/CLAUDE.md`" for `TagIcon` → `components/labels/CLAUDE.md`.
- `ui/screens/CLAUDE.md`: `components/NewItemMenu.kt` → `components/menu/`, `components/SearchableTopAppBar` →
  `components/appBar/`, `components/ListItemAnimation.kt` → `components/list/`.
- `ui/tuner/CLAUDE.md`: `dialogs/TunerSheet` → `dialogs/instrument/TunerSheet`.
- `ui/chords/CLAUDE.md`: `dialogs/ChordShapeSheet.kt` → `dialogs/instrument/ChordShapeSheet.kt`.
- `ui/screens/songDetails/CLAUDE.md` (or wherever plan 11 put the chevron bullet): `components/ExpandChevron.kt` →
  `components/controls/ExpandChevron.kt`.
- README: its Third-party software table names no path; nothing to change unless a grep says otherwise.

## Tests

None new; every test moves with its code (table above), and `SetlistSongToggleTest` is split case by case without
changing a body. Verify with the root CLAUDE.md test command, `:app:desktop:compileKotlin`,
`:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
`python3 .github/scripts/check_sizes.py`, `python3 .github/scripts/check_license_headers.py` and `./gradlew spotlessCheck`.
