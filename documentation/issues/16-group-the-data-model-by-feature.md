<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Group `:data:model`'s flat `domain` package into feature packages, with `DataState` and `Logger` at the root.

**Challenged:** amended — plan 10's `RecoveringTest` (moved into this module's `domain` test package) is placed at the
root with `Logger`; inventory, the expect/actual set and the absence of `@Serializable` classes (whose default serial
names would carry the package) re-checked at HEAD.

**Kind:** structure · **Severity:** low · **Platforms:** all
**Files:** every file under `data/model/src/*/kotlin/com/pandulapeter/campfire/data/model/`; `data/model/CLAUDE.md`
and new `library/` and `preferences/` `CLAUDE.md` files beside the moved code; the 548 files outside it that import
`com.pandulapeter.campfire.data.model.domain.*` (per module: `:presentation` 263, `:domain:implementation` 55,
`:data:sync:implementation` 48, `:data:source:local:implementation` 39, `:domain:api` 38,
`:data:repository:implementation` 27, `:data:source:remote:implementation` 27, `:data:repository:api` 11,
`:data:source:remote:api` 11, `:data:formats` 10, `:data:source:local:api` 7, `:tools:screenshots` 6, `:app:android` 4,
`:app:ios` 4, `:app:desktop` 2).

**Lands last of the restructure plans** (after 10, which adds `LibraryFiles.songBaseName` and `Logger.recovering` here,
and after 11–15 and 17): it touches the import block of almost every file in the project, so anything landing after it
conflicts everywhere. Because the rewrite is a script, re-running it on a later `HEAD` is cheap; never resolve its
conflicts by hand. Drop or add table rows for files that appeared or went since this plan was written.

## Problem

`:data:model` puts 38 of its 39 main files in one package, `com.pandulapeter.campfire.data.model.domain` (only
`DataState.kt` is at the root): the library naming rules, the user preferences, sync's states, the import plan and
budget, the catalogue searches, songs, and setlists, side by side, and the module's `CLAUDE.md` (≈860 words) describes
all of it in one file. The name `domain` says nothing (every model is one). User decision 2 (2026-10-14): restructure
everything into feature subpackages, `data/model` included.

## Fix

New packages `com.pandulapeter.campfire.data.model.<feature>`; the `domain` level disappears. Behaviour is unchanged:
every edit is a `package` line, an `import` line, or a KDoc link.

### Target layout (main sources are `src/commonMain` unless named)

| New package | Files (from `domain/`) | Tests moving with them |
|---|---|---|
| root `data.model` | `DataState.kt` (already there), `Logger.kt` | plan 10's `RecoveringTest` (it tests `Logger.recovering`) |
| `library` | `LibraryFile.kt`, `LibraryFiles.kt`, `LibraryText.kt`, `Accents.kt`, `ExportedFile.kt`, `Normalization.kt` **and its three actuals** `src/iosMain/.../Normalization.ios.kt`, `src/jvmSharedMain/.../Normalization.jvmShared.kt`, `src/wasmJsMain/.../Normalization.wasmJs.kt` | `LibraryFilesTest`, `LibraryFilePredicatesTest`, `LibraryNameIdentityTest`, `LibraryTextDecodingTest`, `src/desktopTest/.../LibraryNameIdentityJvmTest` |
| `song` | `Song.kt`, `SongContent.kt`, `EditedSongs.kt`, `SongLanguage.kt`, `Tag.kt`, `Tags.kt` | `TagsTest` |
| `setlist` | `Setlist.kt`, `ParsedSetlist.kt` | — |
| `preferences` | `UserPreferences.kt`, `MetronomeSettings.kt`, `PrintSettings.kt`, `TunerSettings.kt` | `InstrumentTest`, `UserPreferencesSongMapsTest` |
| `sync` | `SyncState.kt`, `AuthorizationCompletionPage.kt`, `SystemBrowser.kt` | — |
| `importing` | `ImportPlan.kt`, `ImportChoices.kt`, `ImportResult.kt`, `ImportProgress.kt`, `ImportLimits.kt`, `ImportedFile.kt`, `ImportDownload.kt`, `ExtractedDocument.kt` | `ImportChoicesTest` |
| `catalogue` | `Catalogue.kt`, `CatalogueSearchResults.kt`, `CoverArtCandidate.kt`, `CoverArtQuery.kt`, `SongDetailsCandidate.kt`, `SongDetailsQuery.kt`, `SongCredits.kt`, `SongCreditsState.kt` | — |

Choices made: `setlist` is a seventh package rather than part of `song` or `library` (a setlist is its own model,
`:domain` gets a `setlists` package in plan 15, and `ImportPlan` / `UserPreferences` refer to it on their own).
`ExportedFile` goes to `library` (it is library files on their way out; `importing` would misname it). `Accents.kt` and
`Normalization.kt` go to `library` because the file-name rule is their reason to be here; `:domain`'s `NormalizeText`
and `Tags.kt` import them from there.

### The traps

- **Same-package users with no import.** Inside the module, today's one package means these cross-feature uses carry
  no import and break silently into "unresolved reference" after the move (grep at HEAD):
  `UserPreferences.kt` → `LibraryFile`, `LibraryFileKind` (library), `Setlist` (setlist), `Song`, `normalizedTags`,
  `tagKey` (song); `ImportPlan.kt` → `Setlist`, `ParsedSetlist`; `ImportLimits.kt` → `LibraryFiles`;
  `Tags.kt` → `normalizedToNfc`, `UserPreferences`; `SongLanguage.kt` → `Tag`; `TunerSettings.kt` /
  `MetronomeSettings.kt` → `UserPreferences` (same new package, fine). Some are KDoc-only links: add the import anyway,
  or the link silently stops resolving. The tests (package `data.model.domain` today) need the same treatment. Let
  the compiler find the rest: compile `:data:model` for every target (desktop, Android, iOS simulator, wasm) before
  touching any other module.
- **expect/actual share a package.** `Normalization.kt`'s `expect fun String.normalizedToNfc()` and its three actuals
  move in one commit, or every platform fails to link.
- **Nested-class imports.** Seven imports name a nested class (`…domain.UserPreferences.Instrument` ×4, `.Notation` ×2,
  `.SortingMode` ×1): the rewrite keys on the first segment after `domain.`.
- **Top-level functions and typealiases are imported by name too:** `normalizedToNfc` (10 imports), `decodeLibraryText`
  (7), `tagKey` (4), `normalizedTags` (3), `withoutAccent`, `isCombiningMark` (2 each), `CoverArtSearchResults` (5, a
  typealias in `CatalogueSearchResults.kt`). The map below covers them.
- **KDoc fully qualified links** outside the module, e.g. `[com.pandulapeter.campfire.data.model.domain.SystemBrowser]`,
  `[…domain.SyncSummary.havePreferencesFailed]`, `[…domain.SyncDeletionPolicy.DELETE_REMOTELY]`: the script rewrites
  any `com.pandulapeter.campfire.data.model.domain.<Name>` occurrence, not only `import` lines.

### The rewrite script (scratchpad, not committed)

A map `simple name → feature`, built from the declarations of each moved file (`SyncState.kt` alone declares
`SyncProviderId`, `SyncAccount`, `SyncState`, `SyncProgress`, `SyncOutcome`, `SyncDeletionDirection`,
`SyncDeletionPolicy`, `SyncSummary`, `SyncFailureReason`, …; `ImportChoices.kt` also `ImportEntryKey`; `ImportPlan.kt`
also `ImportConflictResolution`; `ImportLimits.kt` also `ImportBudget`; `LibraryFile.kt` also `LibraryFileKind`).
Generate it with a grep of top-level `class|interface|object|typealias|fun` declarations rather than by hand, and make
the script fail on a name it has no entry for. For every `.kt` / `.kts` file in the repository (excluding `build/`):
replace `com.pandulapeter.campfire.data.model.domain.<Name>` with `com.pandulapeter.campfire.data.model.<feature>.<Name>`
(`Logger` → `com.pandulapeter.campfire.data.model.Logger`), then `./gradlew spotlessApply` to re-sort the import
blocks. Run it per commit with only that commit's names in the map.

`tools/screenshots` has the user's uncommitted changes in the working tree (`Device.kt`, `Promo.kt`, `Shot.kt`): change
only import lines there and never stage the rest. The Android Baseline Profile
(`app/android/src/main/generated/baselineProfiles/*.txt`) names the old classes; it goes stale, and prepare-release
regenerates it. Do not edit it by hand.

### Commits (each compiles)

1. `Logger` to the root package, with plan 10's `RecoveringTest`.
2. `library` (with the `Normalization` actuals and the library `CLAUDE.md`).
3. `song`, 4. `setlist`, 5. `preferences` (with its `CLAUDE.md`), 6. `sync`, 7. `importing`, 8. `catalogue` — the last
   one leaves `domain/` empty in every source set; delete the directories.

Each commit: `git mv` the files and tests, fix their `package` lines and the intra-module imports, run the script for
that commit's names over the repository, `spotlessApply`, compile. Check with `git diff --cached -M --stat` (every moved
file a rename) and `git diff --cached --color-moved=dimmed-zebra`. Message e.g. `Group the sync models into a package of
their own.` At the end, `grep -rn "data\.model\.domain"` over code, `CLAUDE.md` files, `.claude/skills`, and README must
find nothing (only `documentation/plans/` history may keep it, and at HEAD none of those name the package).

### CLAUDE.md

References to `:data:model` elsewhere name the module, not the package (checked: `data/formats`, `data/source/*`,
`domain/implementation`, `tuner/api`, `presentation/.../ui/CLAUDE.md`, the root), so they stay true. Inside
`data/model/CLAUDE.md` (≈860 words, module budget 1,500):

| Section today | Goes to | ≈ words |
|---|---|---|
| Intro (fix `domain/Normalization.kt` → `library/Normalization.kt`; say `DataState` and `Logger` are the root's), `## Songs and setlists`, `## Import`, `## Other` | stays in `data/model/CLAUDE.md` | 400 |
| `## Identity and names`, `## Text` | `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/CLAUDE.md` (headings lose their `domain/…` paths) | 320 |
| `## UserPreferences` | `…/data/model/preferences/CLAUDE.md` | 190 |

Songs, setlists, importing, sync, and catalogue keep their few lines in the module file rather than each getting a
file of under 100 words. New files carry the MPL header; run `python3 .github/scripts/check_sizes.py`.

## Tests

No new tests: nothing changes but packages. The nine test files move with their code (table above), and
`:data:model:desktopTest` must run the same number of tests before and after (compare the `build/test-results` XML
counts). Verify with the root CLAUDE.md's full test command (every module's imports change),
`:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, and `:tools:screenshots:compileKotlin`.
