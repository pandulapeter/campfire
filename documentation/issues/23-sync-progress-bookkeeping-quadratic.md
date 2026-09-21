# 23 · A first sync of a large library rescans the whole library back to back and rebuilds the whole index per file

**Severity:** performance (all platforms; worst on a low-end Android phone and on the web's single thread; grows
with the square of the library, so it only shows from around a thousand songs) · **Area:**
`:data:repository:implementation` (`SyncRepositoryImpl`, `SyncEngine`)

## Symptom
A first sync of 3000 songs onto a new device takes minutes. For all of that time the app also reads and parses
every song already on disk again and again — the fans spin, the phone gets warm, the lists stutter, and on the web
build, where everything shares one thread, the UI freezes in bursts — and the run itself is slower than the network
alone would make it, because it competes with its own bookkeeping for IO and CPU.

## Cause
Two pieces of per-file bookkeeping in a run each cost O(library), so a run costs O(library²).

**(a) The live rescan.**
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:334-340`:

```kotlin
private fun scheduleLiveRescan() {
    if (liveRescanJob?.isActive == true) return
    val now = Clock.System.now().toEpochMilliseconds()
    if (now - lastLiveRescanAt < LIVE_RESCAN_INTERVAL_MS) return
    lastLiveRescanAt = now
    liveRescanJob = scope.launch { rescanLibrary() }
}
```

The one second is measured from the *start* of the previous rescan, and a rescan is `SongRepositoryImpl.rescan()`:
the text cache cleared (the ViewModel re-reads every open text), both folders listed, every song read and
summarised, a new list published. Once a scan of the growing library takes longer than a second — the brief's
phone gets there within a few hundred songs — the next progress event after it ends starts another, so rescans
run back to back for the whole run: on the order of a hundred full scans and 10^5 file reads and parses, where the
run's own rescan at the end gives the exact numbers anyway.

**(b) The index snapshot.** `SyncEngine.apply` (`sync/SyncEngine.kt:197-211`) builds a complete
`SyncIndexDocument` under the results lock for **every** finished operation — a new map with one string
concatenation (`key.path`) and one `Entry` per indexed file — although `scheduleIndexWrite` throws away all but one
per two seconds. For 3000 operations that is about 4.5 million entries allocated while the other five transfers
wait on the lock. This half is the smaller one (seconds of CPU over a run, not minutes); it is fixed here because
it is the same shape and the same call site.

Both verified against `29820b93`; the costs are estimates from reading, not measurements.

## Fix
Written against the two files as plans 01–17 leave them (in particular plan 08's `finishRunCutShort` and
`hasFinishedOperations`).

### (a) A live rescan earns its next one
`SyncRepositoryImpl.kt`:

1. Replace `lastLiveRescanAt` and `LIVE_RESCAN_INTERVAL_MS` with a pause that follows the cost of the last rescan,
   measured on the monotonic clock (a duration is what is being compared, so the wall clock has no business here):

   ```kotlin
   private var liveRescanJob: Job? = null

   /** When the last live rescan ended and how long the next one has to wait for, see [scheduleLiveRescan]. */
   @Volatile
   private var lastLiveRescanEnd: TimeMark? = null

   @Volatile
   private var liveRescanPause = LIVE_RESCAN_INTERVAL
   ```

   ```kotlin
   private fun scheduleLiveRescan() {
       if (liveRescanJob?.isActive == true) return
       if (lastLiveRescanEnd?.let { it.elapsedNow() < liveRescanPause } == true) return
       liveRescanJob = scope.launch {
           val duration = measureTime { rescanLibrary() }
           liveRescanPause = liveRescanPauseAfter(duration)
           lastLiveRescanEnd = TimeSource.Monotonic.markNow()
       }
   }
   ```

   and, top level in the same file so that it can be tested:

   ```kotlin
   /**
    * How long the counters wait after a live rescan that took [duration]. A rescan reads the whole library, so on a
    * large one it is the expensive part of keeping them moving: waiting a multiple of what it cost caps the share of a
    * run that goes into re-reading what it has already written, however large the library gets, while a small library
    * keeps the interval that makes the numbers visibly move.
    */
   internal fun liveRescanPauseAfter(duration: Duration) = maxOf(LIVE_RESCAN_INTERVAL, duration * LIVE_RESCAN_PAUSE_FACTOR)

   /** Often enough that the counters visibly move, where the reading costs next to nothing. */
   internal val LIVE_RESCAN_INTERVAL = 1.seconds

   /** One part reading to five parts not: a live rescan never takes more than about a sixth of a run. */
   private const val LIVE_RESCAN_PAUSE_FACTOR = 5
   ```

   Replace the last two sentences of `scheduleLiveRescan`'s KDoc ("Throttled because…") with: "Throttled by what
   the last one cost, see [liveRescanPauseAfter]: there is no per file way into the cache, so one per file would
   re-read everything a few thousand times over a single run. The exact numbers still come from the run's own
   rescan when it ends; this only keeps them moving on the way there."

   Imports: `kotlin.time.Duration`, `kotlin.time.Duration.Companion.seconds`, `kotlin.time.TimeMark`,
   `kotlin.time.TimeSource`, `kotlin.time.measureTime`. The file is already `@file:OptIn(ExperimentalTime::class)`.

2. The rescan a run ends with must not queue up behind a live one that is about to be made worthless. New private
   function, used by the `Completed` branch and by `finishRunCutShort` instead of `rescanLibrary()`:

   ```kotlin
   /**
    * The rescan a run ends with. A live one still reading is stopped first: it started before the last files moved,
    * so this one has to follow it anyway, and would only wait for it. A cancelled read leaves the cached data as it
    * was (see `BaseLocalDataRepository`).
    */
   private suspend fun rescanLibraryAfterRun() {
       liveRescanJob?.cancelAndJoin()
       rescanLibrary()
   }
   ```

Considered and left for later: R8's cheaper alternative, a per-file `refresh(kind, name)` on the two repositories
fed by the engine, which would make the live counters O(1) per file. It needs new members on `SongRepository` and
`SetlistRepository`, a removal path, per-file invalidation of the text cache and a new engine callback — a feature
rather than a fix, and the pause above already bounds the cost.

### (b) The index document is only built when somebody wants it
3. `SyncEngine.synchronize` / `apply`: `onIndexChanged` hands out a way to take the snapshot instead of the
   snapshot:

   ```kotlin
   onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit,
   ```

   and in `apply`, under the results lock:

   ```kotlin
   onIndexChanged {
       SyncIndexDocument.of(
           providerId = provider.id.id,
           accountId = accountId,
           lastSyncedAt = lastSyncedAt,
           index = updated,
       ).copy(isRunInProgress = true)
   }
   ```

   Extend the last paragraph of `apply`'s KDoc: "What is handed out is a way to take the snapshot rather than the
   snapshot, since building one costs as much as the index is long and most of them are never written. It reads
   the pass's own map, so it may be called in exactly two places: inside [onIndexChanged], which runs under the
   lock, and after [synchronize] has returned or thrown, when nothing writes to that map any more. Never from a
   coroutine launched out of [onIndexChanged]."

4. `SyncRepositoryImpl.runSynchronization` keeps the way to take the latest snapshot instead of the snapshot. It
   stays nullable, null until `loadIndex()` has answered, exactly as the document is today — a run that fails before
   that has nothing worth writing, and must not write an empty index over the real one:

   ```kotlin
   var latestIndex: (() -> SyncIndexDocument)? = null
   ...
   val document = loadIndex()
   latestIndex = { document.copy(isRunInProgress = true) }
   ...
   onIndexChanged = { snapshot ->
       latestIndex = snapshot
       hasFinishedOperations = true
       scheduleIndexWrite(snapshot)
   },
   ```

   `latestIndex?.let { saveIndexQuietly(it.copy(isRunInProgress = false)) }` becomes
   `latestIndex?.let { saveIndexQuietly(it().copy(isRunInProgress = false)) }` in the `DeletionsNeedConfirmation`
   branch and in `finishRunCutShort`, whose first parameter becomes `latestIndex: (() -> SyncIndexDocument)?`. Both
   call it once the engine is done with the map, which is the second of the two allowed places.

5. `scheduleIndexWrite` takes the snapshot **synchronously**, and only when a write is due:

   ```kotlin
   private fun scheduleIndexWrite(snapshot: () -> SyncIndexDocument) {
       if (indexWriteJob?.isActive == true) return
       val now = Clock.System.now().toEpochMilliseconds()
       if (now - lastIndexWriteAt < INDEX_WRITE_INTERVAL_MS) return
       lastIndexWriteAt = now
       // Taken here and not in the job: the engine's lock is what makes reading its map safe, and it is only held
       // for as long as this call runs.
       val document = snapshot()
       indexWriteJob = scope.launch { saveIndexQuietly(document) }
   }
   ```

   (Plan 24 then moves this throttle to the monotonic clock as well.)

## Tests
- `SyncEngineTest`: the two tests that collect snapshots (`a run that loses the network keeps the files it already
  transferred in the index`, `an interrupted run keeps the time of the last completed one`) collect the lambdas
  instead — `val snapshots = mutableListOf<() -> SyncIndexDocument>()`, `onIndexChanged = { snapshots += it }` — and
  call them after the run has thrown: `snapshots.last()()` must hold exactly the two finished songs, and every
  `snapshots.map { it() }` the old `lastSyncedAt`. That pins the "after it has thrown" half of the contract.
  Every other test's `onIndexChanged = {}` compiles unchanged.
- New `LiveRescanPauseTest` in `data/repository/implementation/src/commonTest/.../implementation/`:
  `` `a quick rescan is repeated after the ordinary interval` `` (`liveRescanPauseAfter(40.milliseconds) == 1.seconds`)
  and `` `a slow rescan is not repeated until five times what it cost has passed` ``
  (`liveRescanPauseAfter(3.seconds) == 15.seconds`).
- `SyncRepositoryImplTest` (plans 06–17) must still pass; its `a periodic index write that fails…` case exercises
  step 5.

## Verify
- The unit test command and the three compile checks.
- Desktop, a remote folder of 2000+ songs (generate them with a script: one `{title: Song N}` line each) and an
  empty library. Put a `println` of the duration into the live rescan job for the measurement only. Before: a
  rescan line about every second, each longer than the last. After: the gap between two lines is at least five
  times the previous duration, the counters on the Songs tab still move during the run, and the final count is
  exact. Compare the wall time of the whole run before and after.
- Web build, same folder: the page stays responsive during the run.

## Docs
`data/repository/implementation/CLAUDE.md`, `sync/` bullet: "every finished operation hands `SyncRepositoryImpl` a
snapshot, which writes it at most every `INDEX_WRITE_INTERVAL_MS`" becomes "every finished operation hands
`SyncRepositoryImpl` a way to take a snapshot, which it does at most every `INDEX_WRITE_INTERVAL_MS` — building one
costs as much as the index is long — and once more on the way out…"; and add: "The library counts are kept moving
during a run by a live rescan that waits five times what the previous one took (`liveRescanPauseAfter`), so that
re-reading a large library never becomes most of what a run does."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/LiveRescanPauseTest.kt` (new)
- `data/repository/implementation/CLAUDE.md`

## Depends on
06 and 08 (the functions this rewrites), and the rest of the lane before it (01, 09, 10, 16, 17) only because they
share the two files.
