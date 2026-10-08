# Inject the coroutine scope, the monotonic time source and the wall clock of SyncRepositoryImpl and CoverArtRepositoryImpl, and move their tests onto virtual time

**Challenged:** amended — corrected the claim about the disconnect test (already virtual); added the two test-scheduler consequences (init collectors start at the first suspension; runTest auto-advance fires a waiting automatic run inside any wait), the `@ExperimentalTime` opt-in and where the `@Single` function goes if the module object moves.

**Kind:** testability  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/.../data/repository/` — `Module.kt` (`DataRepositoryModule`, new `@Single` function); new `implementation/base/RepositoryEnvironment.kt` (internal); `implementation/SyncRepositoryImpl.kt` (`scope`, `scheduleSynchronization`, `startScheduledSynchronization`, `scheduleLiveRefresh`, `scheduleIndexWrite`, the `Clock.System.now()` in `runSynchronization`, `loadIndex`/`saveIndex`'s `withContext(Dispatchers.Default)`); `implementation/CoverArtRepositoryImpl.kt` (`scope`, `failures`' `TimeSource.Monotonic.markNow()`); tests `SyncRepositoryImplTest.kt` (the `repository(...)` builder, `leaving the app with no automatic run waiting starts nothing`, `stopping sync drops the automatic run that is waiting`, `an automatic run asked for during a run follows it at once when the app leaves`, `awaitOutcome`), `CoverArtRepositoryImplTest.kt` (its builder and the `withContext(Dispatchers.Default) { withTimeout(5_000) { while (…) delay(1) } }` waits), `tools/screenshots` only if it constructs either class (it does not at 2940b0e0a); `data/repository/implementation/CLAUDE.md`
**Depends on:** none (22 builds on it; land it first)

## Problem

Both long-lived repositories build their own world:

```kotlin
private val scope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
        println("A sync job ended in an exception nothing caught: $throwable")
    },
)
…
scheduledRunDueAt.value = TimeSource.Monotonic.markNow() + AUTOMATIC_RUN_DELAY
…
Clock.System.now().toEpochMilliseconds()
```

and `CoverArtRepositoryImpl` the same scope plus `failures[url] = TimeSource.Monotonic.markNow() + UNREACHABLE_RETRY_DELAY`.

So everything they launch runs on real `Dispatchers.Default` threads, outside `runTest`'s scheduler, and the tests can
only wait in real time:

```kotlin
// The run would start on the repository's own dispatcher, so the test waits in real time for one that must not.
withContext(Dispatchers.Default) { delay(200) }
```

`SyncRepositoryImplTest` has two such 200 ms sleeps (`leaving the app with no automatic run waiting starts nothing`,
`stopping sync drops the automatic run that is waiting`) — negative assertions that pass whenever the machine is slow
enough, i.e. they can pass for the wrong reason — and one `withTimeout(5_000)` race; `CoverArtRepositoryImplTest` has
six polling loops. Nothing tests the core of automatic sync, the **ten-second debounce** (`AUTOMATIC_RUN_DELAY`): a
second request moves the start, a request during a run follows it — a test would have to sleep 10 s. The one
`lastSyncedAt` assertion cannot pin the time either. Every repository instance a test builds also leaks a scope that is
never cancelled.

## Fix

1. Add `base/RepositoryEnvironment.kt`:

   ```kotlin
   /** What the long-lived repositories run on, injected so that their tests can run it on virtual time. */
   internal class RepositoryEnvironment(
       /** The parent of every scope [scopeFor] hands out: a SupervisorJob plus Dispatchers.Default in the app. */
       private val context: CoroutineContext,
       val timeSource: TimeSource.WithComparableMarks,
       val clock: Clock,
       /** Where a large document is encoded or decoded off the caller's thread. */
       val computation: CoroutineDispatcher,
   ) {
       /** A scope of its own for one repository, logging what nothing caught as "A [name] job ended in …". */
       fun scopeFor(name: String) = CoroutineScope(
           context + SupervisorJob(context[Job]) + CoroutineExceptionHandler { _, throwable ->
               println("A $name job ended in an exception nothing caught: $throwable")
           },
       )
   }
   ```

   and in `DataRepositoryModule`:

   ```kotlin
   @Single
   internal fun repositoryEnvironment(): RepositoryEnvironment = RepositoryEnvironment(
       context = SupervisorJob() + Dispatchers.Default,
       timeSource = TimeSource.Monotonic,
       clock = Clock.System,
       computation = Dispatchers.Default,
   )
   ```

   Same precedent as `DataRemoteSourceModule.coverArtSearchRemoteSources` (a `@Single` function in the module object for
   something *built*). The log lines stay exactly as today: pass `"sync"` and `"cover art"`.
2. `SyncRepositoryImpl` takes `environment: RepositoryEnvironment`; `scope = environment.scopeFor("sync")`; every
   `TimeSource.Monotonic.markNow()` becomes `environment.timeSource.markNow()` (`scheduledRunDueAt` stays a
   `MutableStateFlow<TimeMark?>`); `Clock.System.now()` becomes `environment.clock.now()`; the two
   `withContext(Dispatchers.Default)` in `loadIndex`/`saveIndex` use `environment.computation`. `measureTime` in
   `scheduleLiveRefresh` becomes `environment.timeSource.measureTime { … }`.
3. `CoverArtRepositoryImpl` the same (`scopeFor("cover art")`, `failures` marks from `environment.timeSource`). Its
   `failures` map holds `ComparableTimeMark?`, hence `WithComparableMarks`.
4. Tests: a helper `TestScope.testEnvironment()` =
   `RepositoryEnvironment(context = backgroundScope.coroutineContext, timeSource = testScheduler.timeSource, clock = a fixed or scheduler-driven Clock, computation = StandardTestDispatcher(testScheduler))`.
   `backgroundScope` cancels everything at the end of the test, which also ends the scope leak. Rewrite:
   - the two `delay(200)` tests as `advanceUntilIdle()` (or `advanceTimeBy(AUTOMATIC_RUN_DELAY * 2)`) then assert
     `provider.listCount == 0`;
   - `an automatic run asked for during a run follows it at once…` without `withTimeout(5_000)`: assert the automatic
     run's marker was written with `testScheduler.currentTime` still below 10 000 ms;
   - `awaitOutcome` keeps working unchanged (it suspends on the state), but no longer needs its "on the repository's own
     dispatcher" KDoc;
   - `CoverArtRepositoryImplTest`'s polling loops as `runCurrent()`/`advanceUntilIdle()`.
   (`a disconnect that is cancelled after the credentials went…` already runs `disconnect` in the test's own coroutine,
   so its `onDisconnect = { delay(1_000) }` is virtual today and the test does not change.)

   Two consequences of moving the repositories onto the test scheduler, to check while rewriting:
   - **The `init` collectors start at the test's first suspension, not at construction.** `LibraryChanges` is a
     `SharedFlow` without replay, so a test that emits on it (or on `syncedPreferencesSync.localChanges`) right after
     building the repository must `runCurrent()` first, or the emission is lost. None does at 2940b0e0a; the new
     debounce tests below must.
   - **`runTest` skips virtual time whenever the test body waits.** A waiting automatic run (`scheduledRunDueAt`
     10 s ahead) therefore fires inside any `awaitOutcome()` / `syncState.first { … }` that waits, where in real time it
     never fired within a test. Every test at 2940b0e0a that calls `scheduleSynchronization()` clears or starts the
     waiting run before it waits (`startScheduledSynchronization`, `cancelSynchronization`), so none changes — re-check
     any test plan 22 or 23 adds or moves.
   `Clock` is `kotlin.time.Clock` (`@ExperimentalTime`): opt in at the file level of `RepositoryEnvironment.kt` and of
   the tests, as `SyncRepositoryImpl.kt` already does. The concurrent lane may move `DataRepositoryModule` to another
   package; put the `@Single` function wherever the object then lives (it is in the object, so `@ComponentScan`'s
   package does not matter for it).
5. Add the missing debounce tests (below). Each of steps 2+4 (sync) and 3+4 (cover art) can be its own commit.

The repositories' behaviour does not change: production gets exactly the dispatcher, job and handler it has today.

## Tests

New in `SyncRepositoryImplTest`, all on virtual time:
- `an automatic run starts ten seconds after the latest change`: `scheduleSynchronization()` at t=0, again at t=6 s;
  no `list()` at t=15.9 s, one run started at t=16 s.
- `a change during a run is carried out after it`: start a run gated on a download, `scheduleSynchronization()`, open
  the gate, advance 10 s: exactly two runs (`listCount` counts passes, so assert via the index marker writes or a run
  counter on the fake).
- `Sync now takes the place of the waiting automatic run`: `scheduleSynchronization()`, `synchronize(ASK)`, advance
  past 10 s: one run.
- `lastSyncedAt is the clock's time`: fixed clock, completed run, `state.lastSyncedAt == clock.now()`.
- Cover art: `an unreachable address is asked again after a minute` (59 s → no request, 61 s → request), if not already
  covered.

Guards: every existing test in `SyncRepositoryImplTest` and `CoverArtRepositoryImplTest`.

## Manual check

On one device with Dropbox connected: edit a song, wait ~10 s, and see the sync indicator run once; edit twice 5 s apart
and see one run ~10 s after the second edit. Background the app right after an edit and confirm the run starts at once
(Android notification). Covers still load in the song list.
