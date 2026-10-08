# Split SyncRepositoryImpl into a thin facade over SyncStateHolder, SyncIndexStore, SyncLibraryRefresher, SyncRunner, SyncRunScheduler and SyncConnectionManager, with SyncEngine and SyncedPreferencesSync injected

**Challenged:** amended — step 1 no longer annotates `SyncEngine` `@Single` with a defaulted parameter (the Koin compiler plugin's default `skipDefaultValues` would silently inject `{ null }` and break the demo-file exception in production); it is built by a `@Single` function in `DataRepositoryModule` instead, and `SyncEngineTest`'s 66 direct calls stay untouched. Corrected the lock-order list (`changedFilesMutex` is innermost, taken under `LibraryFileLock`), made the scheduler tests use a real runner on the fakes, dropped the untestable concurrent-`synchronize` test, and noted that `updateConnected` cannot stay `inline` over a private flow once it is public to the other collaborators.

**Kind:** architecture  ·  **Severity:** high  ·  **Effort:** L  ·  **Risk:** medium  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/.../data/repository/implementation/SyncRepositoryImpl.kt` (all of it); new files in `.../implementation/sync/` — `SyncStateHolder.kt`, `SyncIndexStore.kt`, `SyncLibraryRefresher.kt`, `SyncRunner.kt`, `SyncRunScheduler.kt`, `SyncConnectionManager.kt`; `Module.kt` (`DataRepositoryModule`, a new `@Single fun syncEngine`), `sync/SyncedPreferencesSync.kt` (becomes `@Single`; `SyncEngine.kt` is not annotated); `liveRescanPauseAfter`/`LIVE_RESCAN_INTERVAL` (move with the refresher); tests `SyncRepositoryImplTest.kt` (its `repository(...)` builder), `LiveRescanPauseTest.kt`, `sync/FakeSyncCollaborators.kt`, new `sync/SyncRunSchedulerTest.kt`, `sync/SyncLibraryRefresherTest.kt`, `sync/SyncIndexStoreTest.kt`; `data/repository/implementation/CLAUDE.md` (the long `sync/` paragraph names `SyncRepositoryImpl` for each of these jobs)
**Depends on:** 20 (recovering helper), 21 (injected environment — the new classes take it instead of each building a scope), 23 (demo files leave first). Plan 24 (SyncEngine internals) is independent and may land before or after.

## Problem

`SyncRepositoryImpl` is 1017 lines and does seven jobs at once, sharing one bag of state:

| Job | Members today |
|---|---|
| state | `_syncState`, `updateConnected`, `fail` |
| connection / OAuth | `restore`/`restoreConnection` (+ `restoreMutex`), `connect`, `cancelConnection`, `disconnect`, `forgetStoredConnection(Now)`, `completePendingAuthorization`, `refreshAccount` (+ `accountRefreshJob`), `discardPendingAuthorization`, `forgetCredentialsOf`, `hasStoredCredentials`, `stateBeforeConnecting`, `stateAfterBackingOut` |
| scheduling | `syncJob` (AtomicReference), `scheduledRunDueAt`, the `collectLatest` debounce in `init`, `startRun`, `synchronize`, `scheduleSynchronization`, `startScheduledSynchronization`, `cancelSynchronization` |
| running | `mutex` (the run lock), `runSynchronization`, `synchronizePreferences`, `finishRunCutShort`, `toFailureReason` |
| index persistence | `loadIndex`, `loadIndexOrNull`, `saveIndex`, `saveIndexQuietly`, `scheduleIndexWrite` (+ `indexWriteJob`, `lastIndexWrite`), `json` |
| library refresh | `changedFiles` + `changedFilesMutex`, `liveRefreshJob`, `lastLiveRescanEnd`, `liveRescanPause`, `scheduleLiveRefresh`, `refreshChangedFiles`, `refreshLibraryAfterRun`, `rescanLibrary`, the "wait for the first read" lines at the top of the run |
| demo hashes | `rememberDemoLibraryFiles` (removed by plan 23) |

That is 12 mutable fields and 3 mutexes (`mutex`, `restoreMutex`, `changedFilesMutex`) in one class, and it constructs
its two main collaborators by hand:

```kotlin
private val engine = SyncEngine(libraryFileLocalSource, libraryFileLock, setlistComparison) { key ->
    userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path)
}
private val syncedPreferencesSync = SyncedPreferencesSync(userPreferencesRepository, libraryFileLocalSource)
```

Consequences: the debounce, the live-refresh throttle and the index writer can only be tested through the whole
repository with a fake provider; a reader looking for "why did the disconnect wait?" has to hold the run lock, the
scheduler's `syncJob` and the restore lock in mind at once; and the module's CLAUDE.md spends a 170-line paragraph
attributing everything to one class. The `runSynchronization` body (140 lines) mixes refresh, index and state calls.

## Fix

Behaviour-preserving extraction: every method body moves **verbatim** (only receivers change), every comment moves with
its code, every log line stays byte for byte. Each step is one commit that compiles and passes
`:data:repository:implementation:desktopTest`. All new classes are `internal`, live in `implementation/sync/`, take
`RepositoryEnvironment` (plan 21) where they launch or measure time (each `scopeFor("sync")`, so every job keeps the
"A sync job ended…" log line), and are `@Single` so the Koin compiler plugin
wires them (`SyncProviders` is already a wrapper type; nothing here injects a `List<T>`).

1. **Inject `SyncEngine` and `SyncedPreferencesSync`.** **Do not annotate `SyncEngine` itself.** The Koin compiler
   plugin's `skipDefaultValues` option is on by default (`KoinGradleExtension` of 1.2.1: "non-nullable parameters with
   Kotlin default values will use the default value instead of being resolved from the DI container"), and the
   project does not turn it off — so a `@Single class SyncEngine(…, plantedContentHash: … = { null })` would compile,
   pass the `:app:di` graph check, and silently run production with the `{ null }` lookup, breaking the demo-file
   exception (the root CLAUDE.md's "a demo file this device planted … the cloud folder's version is taken"). Instead
   build it in the module object, as `DataRemoteSourceModule.coverArtSearchRemoteSources`
   does:

   ```kotlin
   /** Built here rather than declared on the class, so that its tests keep the constructor with the default lookup. */
   @Single
   internal fun syncEngine(
       libraryFileLocalSource: LibraryFileLocalSource,
       libraryFileLock: LibraryFileLock,
       setlistComparison: SetlistComparison,
       userPreferencesRepository: UserPreferencesRepository,
   ): SyncEngine = SyncEngine(libraryFileLocalSource, libraryFileLock, setlistComparison) { key ->
       userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path)
   }
   ```

   in `DataRepositoryModule` (wherever the concurrent lane leaves it). `SyncEngine`'s constructor, its
   `suspend (SyncKey) -> String?` parameter and its `{ null }` default stay exactly as they are, so the 66 direct
   `SyncEngine(…)` calls in `SyncEngineTest` (it has no builder helper) do not change, and no `PlantedContentHashes`
   type is needed. `SyncedPreferencesSync` (two parameters, no defaults) is annotated `@Single` — it must be a single
   instance, since the `localChanges` filter only recognizes the values *that same instance's* `synchronize` wrote,
   and both `SyncRunner` and `SyncRunScheduler` reach it. `SyncRepositoryImpl` receives both as constructor parameters.
   **No `@Single` constructor parameter added anywhere in this plan may have a default value**, for the same reason.
2. **`SyncStateHolder`**: `val state: StateFlow<SyncState>`, `fun update(transform)`, `inline fun updateConnected(transform)`
   (body unchanged — but an `internal inline` function may not touch the class's `private` `MutableStateFlow`, so either
   drop `inline`, which costs nothing since every transform is a pure non-suspending lambda, or mark the flow
   `@PublishedApi internal`), `fun fail(providerId, reason, message): Boolean`. `SyncRepositoryImpl.syncState = holder.state`.
3. **`SyncIndexStore`** (`syncStateLocalSource` — or `SyncIndexLocalSource` after plan 27 — and the environment's
   `computation` dispatcher): `load()` (= `loadIndex`, throws), `loadOrNull()`, `save()`, `saveQuietly()`, the `json`
   instance. Pure I/O, no state.
4. **`SyncLibraryRefresher`** (`songRepository`, `setlistRepository`, environment): `awaitFirstRead()` (the two
   `if (… .first() is DataState.Loading) …loadXIfNeeded()` lines), `onFileChanged(key)` (the `changedFilesMutex` add),
   `scheduleLiveRefresh()`, `refreshAfterRun()`, `rescanLibrary()`, plus `liveRescanPauseAfter` and
   `LIVE_RESCAN_INTERVAL` (keep them top-level `internal` so `LiveRescanPauseTest` still compiles). Owns
   `changedFiles`, `changedFilesMutex`, `liveRefreshJob`, `lastLiveRescanEnd`, `liveRescanPause`. **Not per run**: the
   throttle state carries across runs today and must keep doing so.
5. **`SyncRunner`** (providers, engine, syncedPreferencesSync, index store, refresher, state holder, environment):
   `suspend fun run(deletionPolicy, isAutomatic)` (= `runSynchronization`, verbatim), `synchronizePreferences`,
   `finishRunCutShort`, `scheduleIndexWrite` with `indexWriteJob`/`lastIndexWrite` (fields of the runner, not of a
   run, for the same reason as step 4), `toFailureReason`, and `suspend fun <T> withRunLock(block: suspend () -> T): T`
   over the run `mutex`. Keep every `withContext(NonCancellable)` exactly where it is.
6. **`SyncRunScheduler`** (runner, state holder, `LibraryChanges`, `syncedPreferencesSync.localChanges`, environment):
   `syncJob`, `scheduledRunDueAt`, the three collectors now in `SyncRepositoryImpl.init`, `startRun`, `synchronize`,
   `schedule`, `startScheduled`, `cancel`, plus `suspend fun stopForDisconnect()` =
   `scheduledRunDueAt.value = null; syncJob.exchange(null)?.cancelAndJoin()` (the two lines `disconnect` runs today).
   `AUTOMATIC_RUN_DELAY` moves here.
7. **`SyncConnectionManager`** (providers, authenticator, pending authorization store, the forgetting note's local
   source, index store, state holder, scheduler, runner, environment): `restore` (with `restoreMutex`), `connect`,
   `cancelConnection`, `disconnect`, `forgetStoredConnection`, and their private helpers, `stateBeforeConnecting` and
   `accountRefreshJob`. `disconnect` becomes: cancel `accountRefreshJob`; `scheduler.stopForDisconnect()`;
   `withContext(NonCancellable) { providers… ; runner.withRunLock { index.save(null); stateBeforeConnecting = null; state.update { Disconnected } } }`.
8. `SyncRepositoryImpl` is left as the `@Single` facade implementing `SyncRepository` by delegation (≈60 lines), and
   `availableProviders`. Update the module CLAUDE.md: replace "`SyncRepositoryImpl` owns/does X" with the class that
   now does, and add a short "who holds which lock" list:
   - `SyncConnectionManager.restoreMutex` → never held across a run; `forgetStoredConnectionNow` runs under it;
   - `SyncRunner`'s run lock → held by a run, and by `disconnect` around the index deletion only;
   - `LibraryFileLock` → held by the engine around local file calls (and by the song and setlist repositories inside
     their own locks); never held across a request;
   - `SyncLibraryRefresher.changedFilesMutex` → **innermost**: the engine calls `onLocalFileChanged` both inside
     `LibraryFileLock` (`download`, `takeRemote`, `discardCopy`, …) and outside it (after a conflict copy), so it is
     taken under `LibraryFileLock` and must never be held while anything takes `LibraryFileLock` — which holds today,
     since `refreshChangedFiles` releases it before `songRepository.refresh` (which takes `LibraryFileLock`). Keep it
     that way.

**Invariants to re-check in review** (each is commented in the code today and must keep its comment): `startRun` puts
`SyncProgress()` in the state before it returns; the completion handler clears progress for a run cancelled before its
body; a run only writes into a state that is still `Connected`; `finishRunCutShort` waits for (never cancels) the
periodic index write; `restore` answers from a `Connected` state without reading the disk; `connect` never throws
anything but a cancellation.

## Tests

- Existing guard: all of `SyncRepositoryImplTest` (≈60 tests), unchanged except that `repository(...)` builds the
  collaborators by hand (one builder function in `FakeSyncCollaborators.kt`, so each test stays one line).
- New, now possible without a provider:
  - `SyncRunSchedulerTest` (virtual time from plan 21): debounce moves the start; a request during a run follows it;
    `synchronize` replaces a waiting run; `cancel` drops it; `startScheduled` with nothing waiting starts nothing.
    `SyncRunner` is a concrete `@Single` class, so the scheduler is tested with a **real** `SyncRunner` built on the
    existing fakes (`FakeSyncProvider`, `FakeSyncStateLocalSource`, …), counting runs through the index marker writes
    or `provider.listCount` — not with a fake runner, which would need an interface the Koin plugin then has to bind
    (see plan 27 on not relying on supertype binding). Leave out a "two concurrent `synchronize` calls start one run"
    test: on `runTest`'s single-threaded scheduler there is no race to provoke, so it would pass whatever
    `compareAndSet` did.
  - `SyncLibraryRefresherTest`: a refresh cancelled mid-way puts its keys back; `refreshAfterRun` stops a live refresh
    and reads what is left; a second `scheduleLiveRefresh` inside the pause does nothing.
  - `SyncIndexStoreTest`: a document that does not decode loads as an empty index; a storage that throws on read makes
    `load` throw and `loadOrNull` answer null; `saveQuietly` swallows a storage failure.

## Manual check

Dropbox connected, on Android and one desktop: Sync now; Stop mid-run then Sync now again; disconnect during a run;
reconnect (consent page) and back out of it; kill the app mid-run and relaunch (an "interrupted" outcome is shown and no
run starts); edit a song and see the automatic run ~10 s later; background the app right after an edit (Android
notification appears, run completes).
