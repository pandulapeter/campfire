<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Give `ui/state`, the navigation chrome, the editor and the import screen subpackages

**Challenged:** amended — plan 01's `SongFilesControllerTest` and plan 04's `ImportReportStateTest` placed; `FakeSongFiles`
has three users after plan 01, not two; plan 05 already moves `SyncController` (its Files line), so commit 8 is gone;
plans 08 and 09, which edit `state/` files and their tests, must land first.

**Kind:** structure · **Severity:** low · **Platforms:** all (commonMain only; no moved file has an expect/actual)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/*`, the 15 chrome files
in `ui/` (listed below), `ui/components/NavigationItemPresence.kt`, `ui/platform/ContentOverscroll.kt`,
`ui/sync/SyncConnection.kt`, `ui/screens/songEditor/*`, `ui/screens/importReport/*`; tests under
`commonTest/.../ui/state/`, `commonTest/.../ui/` (`NavigationBarItemsTest`, `NavigationChromeKindTest`),
`commonTest/.../ui/screens/songEditor/`; every importer the compiler names; CLAUDE.md files `ui/CLAUDE.md`,
`ui/components/CLAUDE.md`, `ui/screens/songEditor/CLAUDE.md`, new `ui/chrome/CLAUDE.md`,
`ui/state/songText/CLAUDE.md`, `ui/screens/songEditor/toolbar/CLAUDE.md`, `ui/screens/songEditor/search/CLAUDE.md`;
`.github/scripts/size_allowances.json`.

(`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

## Problem

Four flat packages each mix unrelated roles:

- `ui/state/` (27 files): the import queue (`ImportController`, 644 lines, with `ImportOutcome`, `ImportRequest`,
  `ImportSource`, `SongToOpen`), setlist state, song list and selection state, song text storage, the two online
  lookups, sync, and the generic helpers (`AsState`, `DebouncedPreference`, `SavedStateStore`).
- `ui/` itself: the view model and composition root share the package with 15 navigation chrome files (rail, bar,
  scaffold, transitions, tool tabs), which `ui/CLAUDE.md` describes in one 510-word section.
- `ui/screens/songEditor/` (34 files): find and replace and the Shortcuts toolbar are self-contained clusters
  (`EditorSearch*` reach nothing of the toolbar; `EditorToolbar*` / `EditorInsertion*` nothing of the search).
- `ui/screens/importReport/` (18 files): the review's rows and the finished report's rows sit together.

Also `ui/platform/ContentOverscroll.kt` is not a platform seam (commonMain only, no expect): it is the overscroll
modifiers every scrolling container uses, documented in `ui/components/CLAUDE.md`.

## Fix

Moves only, behaviour unchanged, everything stays `internal`. **Order:** after plans 08 and 09 too (08 edits `SongMetadataEditing`, `SelectionEditing`, `CoverArtSearchController`
and two of their tests; 09 `SongPickerState` and `SongListState`), and after plans 01–05 (they add
`SongFilesController`, `SavedDialogRestorer`, `LaunchState`, `ImportReportState`, `SyncConsent.kt` and may already
have moved `SyncController`; place their new files by the tables below) and after plan 12 (which leaves
`NavigationItemPresence` at the components root and `SetlistEntryEdits.kt` at the state root for this plan).

### `ui/state`

| Package (`ui.state.`) | Files | Tests |
|---|---|---|
| `importing` | `ImportController`, `ImportOutcome`, `ImportRequest`, `ImportSource`, `SongToOpen` (+ plan 04's `ImportReportState`) | `ImportControllerQueueTest`, `ImportOutcomeTest`, `SongToOpenTest` (+ plan 04's `ImportReportStateTest`) |
| `setlists` | `SetlistsController`, `SetlistSearch`, `SetlistSongOrder`, `SetlistEntryEdits` (from plan 12) | `SetlistSearchTest`, `SetlistSongOrderTest`, `SetlistEntryEditsTest` |
| `songs` | `SongListState`, `SongSelection`, `SelectionEditing`, `SongPickerState` (+ plan 01's `SongFilesController`) | `SongListStateTest`, `SongSelectionTest`, `SelectionEditingTest` (+ plan 01's `SongFilesControllerTest`, which imports `FakeSongFiles` from `songText`) |
| `songText` | `SongTextStore`, `SongMetadataEditing` | `SongTextStoreTest`, `SongMetadataEditingTest`, `FakeSongFiles` (these two and plan 01's `SongFilesControllerTest` use it) |
| `online` | `CoverArtSearchController`, `SongDetailsLookupController` | `SongDetailsLookupControllerTest` |
| root | `AppExitController`, `PendingExit`, `AsState`, `DebouncedPreference`, `PreferencesController`, `SavedStateStore`, `LibraryState`, `SongLookup`, `ListPlaceholders`, `EmptyPlaceholder` (+ plan 04's `LibraryWriteGate`, if it made one) | `DebouncedPreferenceTest`, `SavedStateStoreTest`, `ListPlaceholdersTest` |

`ListPlaceholders` / `EmptyPlaceholder` stay at the root because both list states use them; `SongLookup` stays with
`LibraryState`, which builds it. `SyncController` goes to `ui/sync/` (below).

### `ui/chrome` (new)

From `ui/`: `ContentEdges`, `DestinationIcon`, `KeyboardAwarePadding`, `NavigationBarItems`, `NavigationChrome`,
`NavigationChromeKind`, `NavigationChromeScaffold`, `NavigationScrim`, `NavigationTransitions`, `ScreenSurface`,
`SharedToolsTabRow`, `ToolScreenFrame`, `ToolsTabRow`, `TopLevelLabel`, `TopLevelScreenSurface`; from
`ui/components/`: `NavigationItemPresence`. Tests `NavigationBarItemsTest`, `NavigationChromeKindTest` move to
`commonTest/.../ui/chrome/`. `ui/` keeps `CampfireApp`, `CampfireContent`, `CampfireScreens`, `CampfireViewModel`,
`AppShortcuts` (+ `AppShortcutsTest`), and the `desktopMain`/`wasmJsMain`/`androidMain`/`iosMain` app shells in the
`ui` package stay where they are (they import from `chrome` as needed).

`ContentEdges` is also used by the export screen, onboarding and editor; it is about the window's edges the chrome
keeps clear of, so it moves with the chrome and they import it.

### `ui/sync`

`ui/state/SyncController.kt` → `ui/sync/` beside `SyncConnection.kt`, unless plan 05 already did; plan 03's
`rememberSyncNotifications` and plan 05's `SyncConsent.kt` land there too. The expect/actual sync seams
(`platform/SyncConsentOrigin*`, `SyncNotificationScheduler`, `BackgroundSync`) stay in `ui/platform`, which is the
package of platform seams. Plan 05 (Files line: `state/SyncController.kt` → `ui/sync/SyncController.kt`) already does this move, so here it is
only a check that nothing of `SyncController` is left in `ui/state`.

### `ui/screens/songEditor`

| Package | Files | Tests |
|---|---|---|
| `songEditor.search` | `EditorSearch`, `EditorSearchMatches`, `EditorSearchSheet`, `EditorSearchShortcuts` | `EditorSearchMatchesTest` |
| `songEditor.toolbar` | `EditorToolbar`, `EditorToolbarButton`, `EditorToolbarMenuButton`, `EditorToolbarToggle`, `EditorInsertion`, `EditorInsertionWrites`, `EditorControlRow`, `TextTranspositionControls` | `EditorInsertionWritesTest` |
| root | everything else (`LoadedSongEditor`, `SongEditorScreen`, `EditorSession`, `ChordProTextField`, `EditorTextReplacement`, `SongPreview`, ...) | the rest |

`EditorTextReplacement` and `ChordProTextField` stay at the root: the search sheet, the field and the request handlers
all use them.

### `ui/screens/importReport`

| Package | Files | Tests |
|---|---|---|
| `importReport.review` | `ImportReviewItems`, `ImportReviewRowItem`, `ImportReviewSectionHeader`, `ImportReviewSections`, `ImportReviewSummaryCard`, `ConflictResolutionDropdown` | `ImportReviewSectionsTest` |
| `importReport.finished` | `ImportReportSections`; `finishedHeader` and `sections` out of `ImportReportItems.kt` (as `ImportResultItems.kt`) | `ImportReportSectionsTest` |
| root | `ImportReportScreen`, `ImportReport`, `ImportReportTopAppBar`, `ImportFailedContent`, `ImportLoadingContent`, `ImportReportButton`, `ImportReportSearchField`, `ReportList`, `ReportNote`, `SectionTitle`; `searchField` out of `ImportReportItems.kt` (into `ImportReportSearchField.kt`, its only Composable's file) | `ImportReportTest` |

`ImportReportItems.kt` is split by declaration, since both stages use `searchField`; the file then goes. The package's
CLAUDE.md (493 words) stays whole: it describes the screen's stages, not files.

### `ContentOverscroll`

`ui/platform/ContentOverscroll.kt` → `ui/components/ContentOverscroll.kt` (root of components). Update the
`(ui/platform/ContentOverscroll.kt)` path in `ui/components/CLAUDE.md`'s overscroll bullet.

### Mechanics

- `git mv`, rewrite `package`, add the imports same-package use never needed (both directions; KDoc links too, ktlint
  counts them). `:app:desktop:compileKotlin` lists the missing ones. `./gradlew spotlessApply`.
- `size_allowances.json`: rename, keeping the value, `ui/state/ImportController.kt` → `ui/state/importing/...` (or
  whatever plan 04 left of it) and `ui/screens/songEditor/EditorSearchSheet.kt::EditorSearchSheet` →
  `.../songEditor/search/...`. Shrunk docs get `--update`.
- `git diff -M --color-moved=dimmed-zebra` per commit shows only package lines and imports; grep for each old path
  afterwards (`CLAUDE.md` files, `.claude/skills/`, `documentation/`).

### Commits (each compiles and passes the tests)

1. `ContentOverscroll` → components.
2. `ui/chrome` with `NavigationItemPresence` and the two tests; `ui/chrome/CLAUDE.md`.
3. `state/importing`. 4. `state/setlists`. 5. `state/songs`. 6. `state/songText` with its CLAUDE.md. 7. `state/online`.
8. `songEditor/search` with its CLAUDE.md. 9. `songEditor/toolbar` with its CLAUDE.md.
10. `importReport/review` and `importReport/finished` (one commit: the `ImportReportItems.kt` split serves both).

### CLAUDE.md

| From | To | Words |
|---|---|---|
| `ui/CLAUDE.md` "`ui/CampfireApp.kt` and the navigation chrome": chrome kind, transitions, `ScreenSurface` touches, chrome hand-over, `NavigationChromeScaffold`, chrome copies under a card, no title bar | `ui/chrome/CLAUDE.md` | ~380 |
| `ui/CLAUDE.md` "The Tools item" (140) | `ui/chrome/CLAUDE.md` | ~140 (file ~520) |
| `ui/CLAUDE.md` view model section: the "Song texts" and "Every write to a song file holds `songWriteMutex`" bullets | `ui/state/songText/CLAUDE.md` | ~170 |
| `songEditor/CLAUDE.md` "Find and replace" (188) | `songEditor/search/CLAUDE.md` | ~185 |
| `songEditor/CLAUDE.md` the Shortcuts rows bullet of "App bar rows", "Shortcut rules" (227), the first two "Folding" bullets | `songEditor/toolbar/CLAUDE.md` | ~360 |

What stays in `ui/CLAUDE.md`'s CampfireApp section (snackbars, rescans, nothing composed before preferences; the
launch screen if plan 03 has not moved it to `ui/launch/`) keeps its heading, with the file list updated to
`CampfireApp.kt`, `CampfireContent.kt`, `CampfireScreens.kt` and a pointer to `ui/chrome/`. `ui/CLAUDE.md` line
"animated by `components/NavigationItemPresence.kt`" → `chrome/NavigationItemPresence.kt`. `ui/CLAUDE.md` and
`songEditor/CLAUDE.md` shrink on their allowances (`check_sizes.py --update`). No other new package gets a
CLAUDE.md: `state/importing`'s rules are `domain/implementation/CLAUDE.md`'s and `importReport/CLAUDE.md`'s, and the
rest have nothing non-obvious.

## Tests

None new; tests move with their code (tables above) and change only `package` and imports. Verify with the root
CLAUDE.md test command, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, `python3 .github/scripts/check_sizes.py`,
`python3 .github/scripts/check_license_headers.py` and `./gradlew spotlessCheck`.
