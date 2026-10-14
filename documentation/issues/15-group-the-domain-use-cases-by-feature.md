<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Group the domain use cases into feature packages, mirrored between `:domain:api` and `:domain:implementation`.

**Challenged:** amended — ordering against plans 11–13 stated (they move `:presentation` files whose imports this plan
rewrites); inventory, helper callers and the Koin scan re-checked at HEAD and unchanged.

**Kind:** structure · **Severity:** low · **Platforms:** all
**Files:** every file under `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/` and
`domain/implementation/src/{commonMain,commonTest}/kotlin/com/pandulapeter/campfire/domain/implementation/`;
`domain/implementation/CLAUDE.md`, new `importing/`, `library/`, `export/`, `songs/` `CLAUDE.md` files beside the moved
code; the importers outside the module: 47 files in `presentation/src`, `app/di/.../CampfireDependencyGraph.kt`,
`app/ios/.../CampfireViewController.kt`, `app/android/.../sync/CampfireSyncService.kt`,
`tools/screenshots/.../Shot.kt`.

**Lands after plan 08** (which deletes the `:chordpro` forwarders) and after plan 09 (which edits `SongCatalog.kt`).
Drop every table row whose file no longer exists at execution time; add a row for any use case created since.
Plans 11–13 move `:presentation` files among the 47 importers rewritten here: merge this lane after theirs and run the
import script on the merged tree rather than resolving import-block conflicts by hand.

## Problem

`:domain:api` keeps 59 use case interfaces (48 once plan 08 lands) in one flat `useCases/` package and 4 models in `models/`;
`:domain:implementation` keeps their 59 `*Impl` classes plus four helpers in one flat `useCases/` package (63 files),
with `ImportPlanner.kt` and `ExportFileNames.kt` at the module root and three mappers in `mapper/`. Sync, cover art,
import, export, setlists, and the screen data sit side by side, so nothing but the file name says what belongs
together, and `domain/implementation/CLAUDE.md` (1,440 words) has to describe all of it in one file. User decision 2
(2026-10-14): restructure everything into feature subpackages.

## Fix

Packages drop the `useCases` / `models` level: `com.pandulapeter.campfire.domain.api.<feature>` and
`com.pandulapeter.campfire.domain.implementation.<feature>`, the same feature names in both modules. A model of
`domain/api/models/` goes to the feature of the use case that returns it. Behaviour is unchanged: every edit is a
`package` line, an `import` line, or a KDoc link.

### Target layout

`Xxx` stands for `XxxUseCase.kt` in `:domain:api` and `XxxUseCaseImpl.kt` in `:domain:implementation`; a test moves
with the file it tests.

| Feature package | Use cases (api + impl) | Other files moved here | Tests moved here |
|---|---|---|---|
| `songs` | `CreateSong`, `DeleteSong`, `DuplicateSong`, `EditSongs`, `GetSongContent`, `GetSongContentInvalidations`, `SaveSongContent`, `RenameSongFile`, `GetEditorDraft`, `SaveEditorDraft`, `SetChordProTag` | api: `models/SongFileRename.kt`; impl: `useCases/SongReferences.kt` | `CreateSongUseCaseImplTest`, `DuplicateSongUseCaseImplTest`, `SongReferencesTest`, `SetChordProTagUseCaseImplTest` |
| `setlists` | `CreateSetlist`, `DeleteSetlist`, `EditSetlist`, `SaveSetlist`, `UpdateSetlist` | — | — |
| `library` | `GetScreenData`, `LoadScreenData`, `DeleteLibrary`, `IsFirstRun`, `RememberDemoLibraryFiles` | api: `models/ScreenData.kt`, `models/SongFilter.kt`, `models/SongSection.kt`; impl: `useCases/SongCatalog.kt`, `useCases/SongSorting.kt`, `useCases/DataStates.kt` | `GetScreenDataUseCaseImplTest`, `DeleteLibraryUseCaseImplTest`, `SongCatalogTest`, `SongSortingTest` |
| `importing` | `PrepareImport`, `ImportFiles`, `DownloadImportFile` | impl: `ImportPlanner.kt` (from the module root) | `ImportPlannerTest` (from the test root), `PrepareImportUseCaseImplTest`, `ImportFilesUseCaseImplTest` |
| `export` | `ExportLibrary`, `ExportSetlist`, `ExportSongs` | impl: `ExportFileNames.kt` (from the module root) | `ExportLibraryUseCaseImplTest`, `ExportSetlistUseCaseImplTest`, `ExportSongsUseCaseImplTest` |
| `text` | `NormalizeText`, `NormalizeSearchText` | — | `NormalizeTextUseCaseImplTest`, `NormalizeSearchTextUseCaseImplTest` |
| `preferences` | `GetUserPreferences`, `SaveUserPreferences`, `UpdateUserPreferences` | — | — |
| `sync` | `CancelSyncConnection`, `CancelSynchronization`, `ConnectSyncProvider`, `DisconnectSyncProvider`, `ForgetSyncConnection`, `GetSyncProviders`, `GetSyncState`, `RestoreSync`, `StartScheduledSynchronization`, `SynchronizeLibrary` | — | `SyncUseCaseImplsTest` |
| `catalogue` | `GetCoverArt`, `GetCoverArtCacheSize`, `ClearCoverArtCache`, `SearchCoverArt`, `SearchSongDetails`, `LoadSongCredits` | — | — |

**Deleted by plan 08, skip** (each impl only forwards to a `:chordpro` object, translating a preference at most):
`ConvertChordProNotation`, `ConvertChordProTextNotation`, `NormalizeLanguageCode`, `ParseChordPro`, `PrettifyChordPro`,
`SetChordProCoverArt`, `SetChordProLanguages`, `SetChordProLinks`, `SetChordProMetadata`, `TransposeChordPro`,
`TransposeChordProText`, with `mapper/AccidentalsMappers.kt` and `mapper/NotationMappers.kt`. Plan 08 keeps
`SetChordProTag` (it adds the library's NFC fold); it goes to `songs`, beside the other writes of a song's text, rather
than into a one-file `chordPro` package. If plan 08 ends up keeping more of them, put the survivors in `songs` too
when they write a song's text, else in a `chordPro` package.

**Stays at the root:** `:domain:api` — nothing (the `useCases/` and `models/` directories end empty and are removed).
`:domain:implementation` — `Module.kt` and `mapper/DocumentMappers.kt` (only `PrepareImportUseCaseImpl` uses it, but the
root CLAUDE.md's convention keeps layer-crossing mappers in a `mapper/` package; moving it to `importing/` is the
alternative, not recommended). Test root (`commonTest/.../domain/implementation/`): `RepositoryStubs.kt` and
`TestModels.kt`, moved there from `useCases/` because tests of six features share them.

Every helper above has callers in one feature only (checked by grep at HEAD: `DataStates` and `SongCatalog` /
`SongSorting` only from `GetScreenDataUseCaseImpl`; `followSongReferences` from the rename and delete impls;
`toChordSheet` from `PrepareImportUseCaseImpl`; `exportFileName` / `songExportFileName` from the export impls;
`ImportPlanner` from the two import impls), so all of them can stay `internal` with no new cross-package calls.

### Koin

`DomainModule` (`Module.kt`) is `@Module @ComponentScan` in `com.pandulapeter.campfire.domain.implementation`; the scan
covers subpackages, which is already proven at HEAD (every `@Factory` lives in the subpackage `.useCases` and is found).
No Koin change; `:app:di`'s compile-time graph check confirms it.

### Rewriting references (the trap)

- **Same-package users with no import.** At HEAD the tests in `useCases/` use `SongRepositoryStub`,
  `SetlistRepositoryStub`, `testSong`, `testSetlist`, `TEST_PREFERENCES` without imports; after the move they need
  them. Production code is safe only because each feature's helpers move with it — re-check with the compiler, not by
  eye.
- **Imports that become wrong rather than missing.** `ImportPlanner` and `ExportFileNames` are imported from the root
  today (`import com.pandulapeter.campfire.domain.implementation.ImportPlanner`); after the move that import names a
  class that no longer exists there. Rewrite it, do not just add the new one.
- **Importers elsewhere.** 271 `import com.pandulapeter.campfire.domain.api.…` lines in 117 files (64 in
  `:domain:implementation`, 47 in `:presentation`, one each in `:app:di`, `:app:ios`, `:app:android`, two in
  `:domain:api`, one in `:tools:screenshots`). Rewrite them with a throwaway script (in the scratchpad, not committed)
  holding the table above as `simple name → feature`: for every `.kt` file, replace
  `import com.pandulapeter.campfire.domain.api.(useCases|models).<Name>` and
  `import com.pandulapeter.campfire.domain.implementation(.useCases)?.<Name>` by the feature package, then
  `./gradlew spotlessApply` to re-sort imports.
- **KDoc links:** one fully qualified link, in `models/SongFileRename.kt`:
  `[com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase]` → `…domain.api.songs.RenameSongFileUseCase`
  (or a plain `[RenameSongFileUseCase]`, since both now share `songs`).
- `tools/screenshots/.../Shot.kt` has the user's uncommitted changes in the working tree: change only its import line
  and never stage the rest of that file.

### Commits (each compiles)

1. Move `RepositoryStubs.kt` and `TestModels.kt` to the test root package; add their imports to every test using them.
2. One commit per feature, in this order: `sync`, `catalogue`, `preferences`, `text`, `setlists`, `export`, `songs`,
   `importing`, `library`. Each commit moves that feature's api files, impl files, helpers and
   tests (`git mv`, then the `package` line) and rewrites every import of exactly those names repository-wide. Message
   e.g. `Group the sync use cases into a package of their own.`
3. The `CLAUDE.md` split (below), in its own commit, or in the feature commit it belongs to.

Check every commit with `git diff --cached -M --stat` (each moved file a rename at ≥ 95% similarity) and
`git diff --cached --color-moved=dimmed-zebra`. Afterwards `grep -rn "domain\.api\.useCases\|domain\.api\.models\|domain\.implementation\.useCases"`
over the repository (code, CLAUDE.md files, `.claude/skills`, `documentation/`) must find nothing but closed plans.
The Android Baseline Profile goes stale (class names in `app/android/src/main/generated/baselineProfiles`); prepare-release
regenerates it, do not edit it by hand.

### CLAUDE.md

`domain/api/CLAUDE.md` (≈450 words) stays one file: its rules span features and are short. `domain/implementation/CLAUDE.md`
(≈1,440 words) is cut along the packages; each new file sits beside the code it describes
(`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/<feature>/CLAUDE.md`,
600-word budget each, with the MPL header):

| Section today | Goes to | ≈ words |
|---|---|---|
| Intro, `## Import` intro line and `### Review (product rule)` | stays in the module file (the root CLAUDE.md's product rules point to `domain/implementation` for it) | 290 |
| `### Preparing`, `### Applying` | `importing/CLAUDE.md` | 480 |
| `## Screen data`, the `DeleteLibraryUseCase` bullet | `library/CLAUDE.md` | 270 |
| `## Export` | `export/CLAUDE.md` | 150 |
| `## Renames and references`: the bold product rule stays in the module file; the `RenameSongFileUseCaseImpl` / `SongReferences` bullet moves, with the `CreateSongUseCaseImpl` and `DuplicateSongUseCaseImpl` bullets | `songs/CLAUDE.md` | 170 |
| `NormalizeText*` and connecting/restoring sync bullets (and whatever plan 08 left of the `:chordpro` bullet) | stay in the module file under "Others with logic" (one bullet each is not worth a file) | 130 |

Fix paths on the way: `useCases/SongReferences.kt` → `songs/SongReferences.kt`; plan 08 has already rewritten the
`:chordpro` use cases bullet. `:presentation`'s
`ui/screens/importReport/CLAUDE.md` says "What the review decides is `domain/implementation/CLAUDE.md`'s" — still true
(the review rule stays there). Run `python3 .github/scripts/check_sizes.py`.

## Tests

No new tests: nothing changes but packages. Every moved test keeps its name and moves with its code (table above); the
count of tests run by `:domain:implementation:desktopTest` must be the same before and after (compare the
`build/test-results` XML counts). Verify with the root CLAUDE.md's test command, `:app:desktop:compileKotlin`,
`:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`, and `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
