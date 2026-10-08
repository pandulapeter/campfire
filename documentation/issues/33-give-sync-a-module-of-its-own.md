# Move sync (the engine, the planner, the index, the synced preferences and the SyncRepository implementation) into a :data:sync:implementation module of its own

**Challenged:** amended — option B (the move) listed gaps that would break the build or tests: `:data:source:local:api` lacks coroutines; plan 20's `recovering` is shared by both halves; plan 23's `DemoLibraryRepositoryImpl` depends on the sync-internal `SyncKey`; `CoverArtRepositoryImplTest` and plan 23's test use fakes that live in `sync/`. Option A (recommended) is unaffected.

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** L  ·  **Risk:** medium  ·  **Platforms:** all
**Files:** new module `data/sync/implementation/` (`build.gradle.kts`, `CLAUDE.md`, `Module.kt` with `@Module @ComponentScan object DataSyncModule`); `settings.gradle.kts`; moved from `data/repository/implementation/src/commonMain/.../implementation/`: `SyncRepositoryImpl.kt` and the collaborators of plan 22, `sync/*` (`SyncEngine`, `SyncPlanner`, `SyncIndexDocument`, `SyncAccountKey`, `SyncedPreferences`, `SyncedPreferencesSync`, plus the files of plans 24 and the concurrent lane's moves), `liveRescanPauseAfter`; their tests (`SyncRepositoryImplTest`, `LiveRescanPauseTest`, `sync/*Test`, `sync/Fake*`, `sync/LibrarySongLocalSource.kt`); `LibraryFileLock.kt` and `LibraryChanges.kt` (move to a module both see — see Fix); `data/repository/implementation/build.gradle.kts` (drops `:data:source:remote:api` unless cover art keeps it — it does: `CoverArtRepositoryImpl`); `app/di/src/commonMain/.../di/CampfireDependencyGraph.kt` (the `@KoinApplication` module list); `app/di/build.gradle.kts`; CLAUDE.md files of both modules, root `CLAUDE.md` (Architecture tree, "six module objects", test command list)
**Depends on:** 20, 22, 23, 24, 27 (move the code once it has its final shape); 21 (`RepositoryEnvironment` must be reachable from the new module — move it with `LibraryFileLock`, or give the sync module its own `@Single`); see "Gaps option B has to close first"

## Problem

After plan 22, sync is ≈2700 of the ≈3900 commonMain lines of `:data:repository:implementation` (at 2940b0e0a:
`SyncRepositoryImpl` 1017 + `sync/` 1696) and ≈3800 of its test lines (`SyncEngineTest` 1887, `SyncRepositoryImplTest`
1119, `SyncedPreferencesTest` 534, fakes ~600). The module's CLAUDE.md is 308 lines, ~190 of them about sync. Any
change to a song or setlist repository recompiles and re-runs the whole sync test suite, and the reverse. The coupling
between the two halves is narrow and already explicit:

- sync → repositories: through `:data:repository:api` only (`SongRepository`/`SetlistRepository` `.songs`, `.setlists`,
  `loadXIfNeeded`, `refresh`, `rescan`; `UserPreferencesRepository`);
- shared internals: `LibraryFileLock` (both sides write library files under it) and `LibraryChanges` (the repositories
  announce, sync listens) — two small `internal` `@Single` classes;
- sync's other inputs are the local and remote source APIs.

## Fix

1. Move `LibraryFileLock` and `LibraryChanges` to `:data:source:local:api` as public classes (they are about writes to
   the library's local files; that module is seen by the repository implementation and, through its `implementation`
   dependency, by the remote implementation — not by `:domain:*` or `:presentation`, so nothing above the data layer
   can take the lock). Keep their `@Single`: the Koin compiler plugin needs `:data:source:local:api` to apply the plugin
   and to be named in `:app:di`'s `@KoinApplication`, or else declare them as `@Single` functions in
   `DataLocalSourceModule` (recommended — no new module object). Commit.
2. Create `:data:sync:implementation` (`campfire-library`, Koin compiler plugin, serialization plugin; `implementation`
   on `:data:repository:api`, `:data:source:local:api`, `:data:source:remote:api`, coroutines, serialization). `git mv`
   the sync files and tests; package `com.pandulapeter.campfire.data.sync.implementation`. `SyncRepositoryImpl` stays
   `@Single internal` there; `:app:di` adds `DataSyncModule` to the `@KoinApplication` and its `implementation`
   dependencies. The compile-time graph check confirms `SyncRepository` still resolves exactly once. Commit.
3. Split the CLAUDE.md: the sync half of `data/repository/implementation/CLAUDE.md` moves to the new module's; update
   the root CLAUDE.md tree and the "six module objects" sentence (seven), and add
   `:data:sync:implementation:desktopTest` to the command list. Commit.

Keep the `sync/` package's `internal` visibility: nothing outside the module needs the engine.

**Gaps option B has to close first** (found in the challenge; each one breaks the build or a test if missed):
- `:data:source:local:api` has no coroutines dependency today (its only dependency is `:data:model`), and
  `LibraryChanges` exposes a `SharedFlow`, `LibraryFileLock` a `Mutex`-backed suspend function: add
  `api(libs.kotlin.coroutines)` there. With the `@Single` functions in `DataLocalSourceModule`, `:data:source:local:api`
  needs neither Koin nor its plugin.
- **Shared helpers of plans 20 and 21.** `base/Recovering.kt` (plan 20) and `base/RepositoryEnvironment.kt` (plan 21)
  are `internal` to `:data:repository:implementation` and used by both halves (`LibraryDeletion`, `CoverArtRepositoryImpl`
  on one side, the sync classes on the other). Either duplicate `recovering` (eight lines) into the sync module and give
  it its own `@Single` environment function, or move both to a module both see — decide before step 2.
- **Plan 23's `DemoLibraryRepositoryImpl`** stays in the repository module and records hashes under `SyncKey.path` with
  `localContentHash` precisely so the engine looks up the same key. `SyncKey` is `internal` to the sync package and
  would move away: move `DemoLibraryRepositoryImpl` (and its test) into the sync module with it, or have it build the
  key text (`SyncKey.path` is `"${kind.id}/$name"`) through a small public helper both modules share — and pin the
  agreement with a test.
- **Test fakes are shared.** `CoverArtRepositoryImplTest` (stays) uses `RecordingSongRepository` from
  `sync/FakeSyncCollaborators.kt`, and plan 23's `DemoLibraryRepositoryImplTest` (stays, unless moved per the previous
  point) uses `FakeLibraryFileLocalSource` and `FakeUserPreferencesRepository`. Split the fakes first: the ones about
  the repositories and the local source stay (or are copied), the provider/authenticator/state ones move.
- Plan 34's `Logger` `@Single` is declared in `DataRepositoryModule`; that is fine across modules (the graph is checked
  at `:app:di`), but say in both CLAUDE.md files where it lives.

## Tests

None new; every moved test moves with its code. Run `./gradlew desktopTest` (all modules) and build the four apps
(`:app:android:assembleDebug`, `:app:desktop:run` smoke, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, the web
compile) — the Koin graph check runs at `:app:di` compile time.

## Manual check

Dropbox connected on one platform: launch (a run starts), edit a song (automatic run ~10 s later), Sync now, disconnect
and reconnect.

## Decision

- **A — keep sync in `:data:repository:implementation`, as the `sync/` package plus the plan-22 classes (recommended
  for now).** After plans 22 and 24 the code is already split into small, separately tested classes behind `internal`;
  a module adds a public `LibraryFileLock`/`LibraryChanges` (step 1) that today nobody outside the module can touch,
  and one more Koin module object, for a build-time gain on a module that compiles in seconds. Revisit when a second
  provider arrives or the module's test time becomes noticeable.
- **B — create `:data:sync:implementation` as above.** Cleaner ownership (the repository module goes back to "the
  library's caches"), separate test runs, and a CLAUDE.md per concern; cost is step 1's widened visibility and the
  churn of moving ~6500 lines of code and tests.
