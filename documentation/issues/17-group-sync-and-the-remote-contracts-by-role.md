<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Group `:data:sync:implementation` into run, scheduling, connection, index, and preferences packages, and `:data:source:remote:api` into sync and catalogue

**Challenged:** amended — commit 1 also rewrites the four platform authenticators of
`:data:source:remote:implementation`, which the module's `desktopTest` does not compile; each commit now compiles every
target of the modules it touches. Inventory (24 main files once plan 10 deletes `Recovering.kt`), the Koin scan and the
allowance key re-checked at HEAD.

**Kind:** structure · **Severity:** low · **Platforms:** all
**Files:** every file under `data/sync/implementation/src/{commonMain,commonTest}/kotlin/com/pandulapeter/campfire/data/sync/implementation/`;
every file under `data/source/remote/api/src/{commonMain,commonTest}/kotlin/com/pandulapeter/campfire/data/source/remote/api/`;
23 files of `data/source/remote/implementation` and 4 of `data/repository/implementation` (imports only);
`data/sync/implementation/CLAUDE.md`; new `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/{run,scheduling,connection,index,preferences}/CLAUDE.md`;
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/CLAUDE.md` (one pointer);
`.github/scripts/size_allowances.json` (the `SyncEngine.kt` key)

Land after plan 10 (it deletes `Recovering.kt` / `RecoveringTest.kt` here in favour of `Logger.recovering` in
`:data:model`), and before plan 16, which lands last of the restructure plans because its import rewrite is a script
that runs wherever these files then are. If plan 10 has not landed,
`Recovering.kt` and its test stay at the root.

## Problem

`com.pandulapeter.campfire.data.sync.implementation` is one flat package of 24 main files and 30 test files: the run
itself (the engine, the planner, the deletion guard, conflicts, name folding), its scheduling and lifetime, the
connection, the index, and the synced preferences, side by side. Its `CLAUDE.md` is already sectioned by exactly those
roles and stands at 1,443 of its 1,500 words, so nothing more can be said about any of them without cutting another.

`com.pandulapeter.campfire.data.source.remote.api` keeps eleven contracts at its root — six of sync
(`SyncProvider`, `SyncConnection`, `SyncFolder`, `SyncProviders`, `SyncAuthenticator`, `PendingAuthorizationStore`),
four of the catalogue searches and the cover download — and a `model/` package of which seven files are sync's and one
(`CoverArtDownload`) the cover download's.

## Fix

Behaviour is unchanged: only packages, imports, and docs move. Everything in `:data:sync:implementation` stays
`internal`; the cross-package references the split creates are all within the module, where `internal` reaches.

**Koin still finds every moved definition.** `@ComponentScan` on `DataSyncModule` (no package given) scans its own
package and every subpackage: `:data:source:remote:implementation` already relies on this, with `@Module @ComponentScan`
in its root `Module.kt` and `@Single internal class`es in `auth/` and `coverArt/` (`PendingAuthorizationStoreImpl`,
`CoverArtRemoteSourceImpl`). `DataSyncModule` and its two `@Single` functions stay at the root (`syncEngine` gains an
import of `run.SyncEngine`). The compile-time graph check in `:app:di` fails the build if anything is missed.

### `:data:sync:implementation` (`com.pandulapeter.campfire.data.sync.implementation.` prefix)

| File | After | Why |
|---|---|---|
| `Module.kt`, `SyncRepositoryImpl.kt`, `DemoLibraryRepositoryImpl.kt` | root | the module's definitions and its two public faces |
| `SyncEnvironment.kt`, `SyncStateHolder.kt` | root | used by scheduling, connection, index, and the repository alike |
| `SyncModels.kt` (`SyncKey`, `LocalFileState`, `RemoteFileState`, `SyncIndexEntry`, `SyncOperation`) | root | used by run, index, scheduling (`SyncLibraryRefresher`), and `DemoLibraryRepositoryImpl` |
| `SyncEngine.kt`, `SyncRun.kt` (`SyncRun`, `SyncPass`), `SyncPlanner.kt`, `DeletionGuard.kt`, `ConflictResolver.kt`, `PassListing.kt`, `SyncNameFolding.kt`, `SyncSummaries.kt` | `run` | one run against one folder; `SyncSummaries`' `plus` is used only by `SyncEngine` |
| `SyncRunScheduler.kt`, `SyncRunner.kt` (with `toFailureReason`), `SyncLibraryRefresher.kt` | `scheduling` | when a run starts, the run lock, and what follows it |
| `SyncConnectionManager.kt` | `connection` | restore, connect, disconnect |
| `SyncIndexDocument.kt`, `SyncIndexStore.kt`, `SyncAccountKey.kt` (`indexKey`, `legacyIndexKey`) | `index` | the index and the key it is filed under |
| `SyncedPreferences.kt`, `SyncedPreferencesDocument.kt`, `SyncedPreferencesSync.kt` | `preferences` | `preferences.json` |

Cross-package uses this creates (all `internal`, all fine): `SyncedPreferencesSync` → `run.foldRemoteNamesOntoLocal`;
`run.PassListing` and `index.SyncIndexDocument` → `preferences.SyncedPreferencesDocument`; `connection` →
`scheduling.SyncRunner.toFailureReason`, `scheduling.SyncRunScheduler`, `index.SyncIndexStore`.

Tests (`commonTest`, same package as their subject):

| Test | After |
|---|---|
| `SyncEngineTest`, `SyncEngineConflictTest`, `SyncEngineDeletionGuardTest`, `SyncEngineNameFoldingTest`, `SyncEnginePlantedDemoTest`, `SyncEngineSetlistDateTest`, `SyncEngineUnreadableFileTest`, `SyncPlannerTest`, `DeletionGuardTest`, `PassListingTest` | `run` |
| `SyncRunSchedulerTest`, `SyncLibraryRefresherTest`, `LiveRescanPauseTest` | `scheduling` |
| `SyncConnectionManagerTest`, `SyncConnectionManagerForgettingTest` | `connection` |
| `SyncIndexDocumentTest`, `SyncIndexStoreTest`, `SyncAccountKeyTest` | `index` |
| `SyncedPreferencesTest` | `preferences` |
| `SyncRepositoryImplTest`, `DemoLibraryRepositoryImplTest`, and the shared fixtures `SyncEngineFixtures`, `FakeSyncCollaborators`, `FakeLibraryFileLocalSource`, `FakeSyncProvider`, `RecordingLogger`, `TestSyncEnvironment`, `TestSyncRepository` | root (the fixtures are used by tests of four or more packages; the moved tests import them) |

`SyncEngine.kt` (595 lines) keeps its allowance: in the commit that moves it, rename its key in
`.github/scripts/size_allowances.json` to
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncEngine.kt`
by hand, value unchanged (`check_sizes.py --update` only lowers and drops keys, so it would drop the old key and the
moved file would then fail the check).

### `:data:source:remote:api` (`com.pandulapeter.campfire.data.source.remote.api.` prefix)

| File | After |
|---|---|
| `SyncProvider.kt` (with `SyncRunEndingException` and its three subclasses), `SyncConnection.kt`, `SyncFolder.kt`, `SyncProviders.kt`, `SyncAuthenticator.kt`, `PendingAuthorizationStore.kt` | `sync` |
| `model/RemoteFile.kt`, `RemoteListing.kt`, `RemoteDocument.kt`, `RemoteDeletion.kt`, `RemoteWriteResult.kt`, `RemoteAuthorization.kt`, `RedirectUri.kt` | `sync.model` |
| `CoverArtSearchRemoteSource.kt`, `SongDetailsSearchRemoteSource.kt`, `CatalogueSearchException.kt` | `catalogue` |
| `CoverArtRemoteSource.kt`, `ImportDownloadRemoteSource.kt` | root (unchanged): the two downloads from an address |
| `model/CoverArtDownload.kt` | root, beside `CoverArtRemoteSource`; `model/` is then empty and goes |
| `hashing/Sha256.kt`, `hashing/LocalContentHash.kt` | `hashing` (unchanged): `Sha256` is used by sync, the Dropbox provider, and the repositories' cover cache |

Tests: `SyncRunEndingExceptionTest` → `sync`, `model/RedirectUriTest` → `sync.model`, `hashing/Sha256Test` unchanged.

### Commits (each compiles for every target and passes the module's `desktopTest`)

`desktopTest` compiles only `commonMain`, `jvmSharedMain` and `desktopMain`. The sync contracts are also imported by
`AndroidSyncAuthenticator.kt` (`androidMain`), `IosSyncAuthenticator.kt` (`iosMain`) and `WebSyncAuthenticator.kt`
(`wasmJsMain`) of `:data:source:remote:implementation`, so commit 1 runs `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs` and `:app:ios:linkDebugFrameworkIosSimulatorArm64` (or the module's own
`compileKotlinIosSimulatorArm64` / `compileKotlinWasmJs` / `compileAndroidMain`) before it is committed.

Within each commit: `git mv`, change the `package` line, then rewrite references with one substitution per moved name
across the repository (it covers imports and fully qualified KDoc links alike), add the imports the compiler asks for,
and grep each touched package for the moved names inside KDoc `[...]` to add the imports KDoc needs (ktlint's
`no-unused-imports` counts a KDoc reference as a use). Check each with `git diff -M --color-moved=dimmed-zebra`: only
`package` and `import` lines may differ.

1. `Move the sync contracts of :data:source:remote:api into a sync package.` — the `sync` and `sync.model` rows and
   their tests, plus `CoverArtDownload` up to the root. Touches most of the 23 importing files of `:data:source:remote:implementation`,
   21 of `:data:sync:implementation`, and the repository module's `CoverArtDownload` importers.
2. `Move the catalogue searches of :data:source:remote:api into a catalogue package.` — touches
   `:data:source:remote:implementation`'s catalogue clients and the 4 importers in `:data:repository:implementation`.
3. `Move the run of :data:sync:implementation into a run package.` — with the `size_allowances.json` key.
4. `Move sync's scheduling into a package of its own.`
5. `Move sync's connection into a package of its own.`
6. `Move the sync index into a package of its own.`
7. `Move the synced preferences into a package of its own.`
8. `Give sync's packages CLAUDE.md files of their own.` — the doc split below.

Find the importers outside each module with
`grep -rlE "data\.source\.remote\.api\.(Sync[A-Za-z]*|PendingAuthorization[A-Za-z]*|CatalogueSearchException|CoverArtSearchRemoteSources?|SongDetailsSearchRemoteSources?|model\.[A-Za-z]+)\b" --include='*.kt' . | grep -v /build/`;
no app module, `:presentation`, or `:domain` module imports these packages, and `:data:sync:implementation` exports
nothing. No skill names a moved path; `documentation/plans/sync-scenario-tests.md` names only the module's
`src/commonTest` and `CLAUDE.md`, which stay valid.

### CLAUDE.md split (commit 8)

`data/sync/implementation/CLAUDE.md` is 1,443 words (measured as `check_sizes.py` does). Move each section next to its
code, cut nothing, and reword only the pointers:

| Section | Goes to | ≈ words |
|---|---|---|
| Intro, "Locks" (the locks span scheduling, connection, run, and the repositories), the first bullet of "Invariants" (content decides, an edit beats a deletion, no merge — the module-wide rules the root `CLAUDE.md` points here for), "Renames" (the root points here for it), the test command | stays in `data/sync/implementation/CLAUDE.md` | 250 |
| The rest of "Invariants" (filters, name matching, what cannot be read, conflicts, a download decided twice, the deletion guard, failures) | `run/CLAUDE.md` | 525 |
| "The index" | `index/CLAUDE.md` | 175 |
| "Scheduling and lifetime" | `scheduling/CLAUDE.md` | 130 |
| "Connection" | `connection/CLAUDE.md` | 175 |
| "Synced preferences" | `preferences/CLAUDE.md` | 220 |

Each package file starts with the license header and `# data/sync/<package>`; the module file says once that each
package's rules are in its own `CLAUDE.md`. `run/CLAUDE.md` is the tightest (≈ 525 of 600); if a later rule pushes it
over, the deletion guard's three sub-bullets are the first candidate to shorten, not to split. Re-aim
`presentation/.../ui/playing/CLAUDE.md`'s "synced, see `data/sync/implementation/CLAUDE.md`" at the `preferences`
package file. The root `CLAUDE.md` names only the module (`data/sync/implementation`, for the rename rule and the
Sync invariants); both stay true, since the module file keeps those rules and points to its packages. Leave it.

`data/source/remote/api/CLAUDE.md` (479 words) stays one module file: its sections are already split by role ("Sync
provider contract", "Authorization", "Hashing", "Other contracts") and none would justify a package file of its own.
Only rename "Other contracts" to "Cover art, import download, and catalogue searches" so the reader can find the
`catalogue` package's rules.

Run `python3 .github/scripts/check_sizes.py` and `python3 .github/scripts/check_license_headers.py`.

## Tests

None new: a move changes no logic. `./gradlew :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest`
must report the same test counts before and after (compare `build/test-results`); `DesktopSyncAuthenticatorTest` holds
port 53682, so rerun it alone before calling it red. Then the root CLAUDE.md's full test command,
`:app:desktop:compileKotlin` (runs `:app:di`'s Koin graph check), `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, and `./gradlew spotlessCheck`.
