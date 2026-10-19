<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Retry a run that failed for the network, after a capped back-off, while the app is alive

**Challenged:** amended — `afterRun` arms a retry only while its own coroutine is still active (a Stop or Disconnect can
end `runner.run` normally when a provider's exception wins over the cancellation, so "a stopped run throws" does not
hold), and `stopForDisconnect` clears the schedule again after its join; the offline cost is restated for lane T's
retries (a run that cannot reach Dropbox now takes ~14 s offline and minutes on a stall, not "a moment"); the
UNKNOWN test throws from `list()` (a plain exception from one upload fails only that file and ends in `Success`); the
"Manual check" section is removed.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** S
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunScheduler.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunSchedulerTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`

## Problem

A run that fails because the network is down, behind a captive portal, or not yet back after waking is never tried
again on its own. The edit that scheduled it stays off the cloud folder until the next change, a tap on Sync now, or
the next launch, and the other devices' changes don't arrive either. If another device edits the same file meanwhile,
the late run makes a conflict copy that a prompt retry would have avoided.

`SyncRunner.run` only records the failure (`scheduling/SyncRunner.kt`, the `catch (exception: Exception)` branch):

```kotlin
} else {
    it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason()))
}
```

and the scheduler's debounce has already cleared the due time before starting the run (`scheduling/SyncRunScheduler.kt`):

```kotlin
scheduledRunDueAt.collectLatest { dueAt ->
    if (dueAt == null) return@collectLatest
    delay(-dueAt.elapsedNow())
    syncJob.load()?.join()
    if (scheduledRunDueAt.compareAndSet(dueAt, null)) startRun(SyncDeletionPolicy.ASK, isAutomatic = true)
}
```

Nothing re-arms `scheduledRunDueAt`, so `startScheduled()` (the app leaving the front) also finds nothing waiting.
Probe at the reviewed commit: a scheduled run whose `list()` throws `SyncNetworkException` ends in
`Failure(NETWORK)` with `listCalls = 1`; with the network back, six virtual hours later it is still `listCalls = 1`.

## Fix

In `SyncRunScheduler` only. Nothing changes in the runner, the engine, or the UI.

1. **Find out how the run ended.** In `startRun`, the launched body becomes
   `runner.run(deletionPolicy, isAutomatic); afterRun()`. `afterRun` reads
   `(stateHolder.value as? SyncState.Connected)?.lastOutcome`. That read is safe without changing `runner.run`'s
   signature: the job is still `syncJob` and still active, so `startRun` refuses any other run until it returns, and a
   disconnect leaves the state not `Connected`, which reads as "no retry". A stopped run usually throws
   `CancellationException` out of `runner.run`, but not always: `SyncRunner` itself notes that "a provider's exception
   can win over the cancellation that caused it", and then `run` records `Failure(NETWORK)` and returns normally from a
   job that Stop or Disconnect has already cancelled. So `afterRun` first checks `currentCoroutineContext().isActive`
   and arms nothing (and leaves the count alone) when it is false. What is left is a Stop landing in the instant between
   that check and the compare-and-set below, which costs one automatic run later and is accepted.
   (The alternative, making `runner.run` return the outcome it wrote, adds one return value to each of its five exits.
   It works but isn't needed.)
2. **Re-arm only for `SyncOutcome.Failure(SyncFailureReason.NETWORK)`.** Never for AUTHORIZATION (that is
   `ConnectionFailed` anyway), STORAGE, REMOTE_STORAGE_FULL, UNKNOWN, `Interrupted`, or `DeletionsNeedConfirmation`,
   which waits for the user's answer. Re-arm with `scheduledRunDueAt.compareAndSet(null, retryAt)`, never a plain
   assignment: a change made during the run has already set an earlier due time (`AUTOMATIC_RUN_DELAY` after it), and
   that one must win. The retry is then simply that run. The retry, like every automatic run, uses
   `SyncDeletionPolicy.ASK`, even when the failed run was started with an answer to the deletion question. An answer
   covers one run (`run/CLAUDE.md`), so the retry asks again.
3. **Tell a retry from a change.** `startScheduled` must not treat a retry 30 minutes out as "a change is waiting". Make
   the flow's value a small private class, `ScheduledRun(val dueAt: ComparableTimeMark, val isRetry: Boolean)`
   (`environment.timeSource` is a `TimeSource.WithComparableMarks`). `schedule()` writes `isRetry = false`, and
   overwrites unconditionally as it does today: a change's due time is at most `AUTOMATIC_RUN_DELAY` away, which is
   never meaningfully later than a pending retry, and a change makes the run a real one. The debounce and `startScheduled`
   compare-and-set the whole `ScheduledRun`, as they compare the `TimeMark` today.
4. **The back-off.** The scheduler holds a failure count (an `AtomicInt`, since `synchronize` is called from the main
   thread and `afterRun` from the scope's), not persisted: a relaunch starts a launch run anyway. A NETWORK failure
   increments it and arms the retry at `markNow() + NETWORK_RETRY_DELAYS[min(count, size) - 1]`. Any other outcome
   resets it to 0, and so does `synchronize()` (a run somebody asked for). The schedule goes in the companion, next to
   `AUTOMATIC_RUN_DELAY`, as `internal val NETWORK_RETRY_DELAYS: List<Duration>`, so tests read it from there.

   **Decided by the user (2026-10-19), as recommended:** 1, 2, 5, 15, and 30 minutes, then every 30 minutes for as long as the
   app stays alive. Offline, that costs one listing attempt every 30 minutes on a desktop left open, and moves nothing.
   How long each retry shows its progress row depends on lane T: with plan 01 a failure without an answer is retried
   three times inside the transport, so an offline device shows "syncing" for ~14 s per retry (2 + 4 + 8 s), and a
   stalled link (timeouts, retried six times) for ~8.6 minutes natively and up to ~15.6 on the web with plan 03, so on
   a stall the row is "syncing" most of the time until the 30-minute steps. Stop ends it and drops the retry. Every
   permanent failure lane T classifies as the network (a proxy's `{}` or HTML page, plans 07 and 08; a hash mismatch,
   plan 06; a requested wait over the cap, plan 02) is retried on the same capped schedule, which is the intent: each
   costs one listing at most every 30 minutes.
5. **Leaving the front.** (A retry that is already *running* when the app leaves is handed to the keep-alive like any
   automatic run in progress, as today; on a stall that is a foreground notification for the rest of that run.) In
   `startScheduled`, a `ScheduledRun` with `isRetry && !dueAt.hasPassedNow()` counts as
   nothing waiting: return the current progress (normally null) and leave it armed. A phone's keep-alive is for
   carrying a change the user just made, and a pending network retry started offline would only fail at once inside a
   foreground service. A retry whose time has passed is the debounce's to start at this instant anyway, so it is started
   and handed over like any waiting run (the existing branches). If the process is frozen or killed in the background,
   the next launch's run carries the change, as it does today.
6. **Ways out.** `cancel()` (Stop) and `stopForDisconnect()` already set `scheduledRunDueAt.value = null`. Keep that
   (first, so the debounce cannot start a run between the stop and the clear), and reset the failure count there too.
   `stopForDisconnect()` also sets it to null once more after `cancelAndJoin()`, which closes the window step 1 leaves
   for a disconnect: the join waits for `afterRun`. `schedule()` already returns while not `Connected`. Check the same in `afterRun`
   (step 1 covers it).
7. **The debounce needs no change.** `collectLatest` restarts on every new value, so a 30-minute `delay` is cancelled
   the moment `schedule()` writes a due time 10 s away, which is the earlier-wins behaviour wanted. A retry armed from
   inside the run's own job is picked up by the collector, which delays, then `join()`s the finishing job and starts
   it. On Android, `TimeSource.Monotonic` doesn't advance in deep sleep, so a retry can land later than its nominal
   time after the device sleeps. That is harmless: the next foreground change or launch overtakes it.

This adds no clock to change detection. Content still decides what changed. The back-off only decides *when* to look
again.

`scheduling/CLAUDE.md`, a new bullet: "A run that ends in `Failure(NETWORK)` (only that reason) is tried again while
the app is alive, after `NETWORK_RETRY_DELAYS` (capped, reset by any other ending and by a run somebody asks for); a
change waiting already wins, Stop and Disconnect drop it, and leaving the front hands it to the keep-alive only once
it is due."

## Tests

In `SyncRunSchedulerTest`, on its `world()` with virtual time (`advanceTimeBy` / `runCurrent`, never
`advanceUntilIdle`). Add `var onList: suspend () -> Unit = {}` to `FakeSyncProvider`, called first in `list()` (and
documented in its KDoc with the other hooks). `world()` gains a way to reach the provider. Delays are read from
`NETWORK_RETRY_DELAYS` and `AUTOMATIC_RUN_DELAY`, never written as literal minutes.

- `a run the network failed is tried again`: `onList` throws `SyncNetworkException` once, `schedule()`, and advance past
  `AUTOMATIC_RUN_DELAY`: one run, `Failure(NETWORK)`. Advance just short of `NETWORK_RETRY_DELAYS[0]`: still one.
  Past it: two runs, and the outcome is `Success`.
- `the wait grows with each failure and starts over after a run that worked`: list always fails. The second retry comes
  `NETWORK_RETRY_DELAYS[1]` after the second failure, not `[0]`. Let it succeed, then fail once more: the next retry
  comes after `[0]` again.
- `a failure that is not the network's is not tried again`: `onList` throws a plain `IllegalStateException`, so the
  run ends in `Failure(UNKNOWN)` (one from `onUpload` would only fail that file and end in `Success`); and once more
  with `onUpload` throwing `SyncRemoteStorageFullException` (`Failure(REMOTE_STORAGE_FULL)`). Six virtual hours later,
  still one run each.
- `a run stopped while the network fails it is not tried again`: `onList` suspends (`awaitCancellation()`) and, when
  cancelled, throws `SyncNetworkException` in place of the cancellation (the provider's exception winning). `cancel()`;
  whatever the outcome reads, no second run starts after the longest delay. The same through `stopForDisconnect()`.
- `a change made while a retry waits starts after the usual delay`: after a NETWORK failure, `schedule()` → a run after
  `AUTOMATIC_RUN_DELAY`, well before `NETWORK_RETRY_DELAYS[0]`.
- `Stop drops a waiting retry`: after the failure, `cancel()` → no further run after the longest delay.
- `leaving the app with only a retry waiting starts nothing`: after the failure, `startScheduled()` returns null and no
  run starts before the retry's own time.
