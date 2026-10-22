/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalAtomicApi::class)

package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * When a run starts: at once when it is asked for, or - for the automatic one every change asks for - once the library
 * has stayed unchanged for [AUTOMATIC_RUN_DELAY], and never while another is going. A run the network failed is tried
 * again after [NETWORK_RETRY_DELAYS], or as soon as the device is back online, for as long as the app is alive.
 */
@Single
internal class SyncRunScheduler(
    private val runner: SyncRunner,
    private val stateHolder: SyncStateHolder,
    libraryChanges: LibraryChanges,
    syncedPreferencesSync: SyncedPreferencesSync,
    private val connectivityMonitor: ConnectivityMonitor,
    private val environment: SyncEnvironment,
) {

    /**
     * A run belongs to the app, not to whatever screen started it. This scheduler is a singleton, so a sync
     * carries on while the user moves around the app, and on Android it survives the activity being destroyed -
     * which is what lets a foreground service keep it going after the app has been left.
     *
     * Nothing launched here may close the app, see [SyncEnvironment.scopeFor]: sync is something the app does
     * on the side.
     */
    private val scope = environment.scopeFor("sync")

    /**
     * The run that is going, or the last one. Swapped atomically rather than assigned: the buttons start and stop runs
     * from the main thread, while an automatic run is started from this scope's own, and two starters that both found
     * no run going would each start one, the second one then being the only one Stop can reach.
     */
    private val syncJob = AtomicReference<Job?>(null)

    /** The automatic run [schedule] or a network failure asked for, or null while none is waiting. */
    private val scheduledRun = MutableStateFlow<ScheduledRun?>(null)

    /**
     * How many runs in a row the network failed, which picks the wait before the next try. Atomic because a run asked
     * for resets it from the main thread while a run ending counts on this scope's. Not persisted: a launch runs anyway.
     */
    private val networkFailureCount = AtomicInt(0)

    init {
        scope.launch { libraryChanges.changes.collect { schedule(reason = RunReason.LIBRARY_CHANGE) } }
        scope.launch { syncedPreferencesSync.localChanges.collect { schedule(reason = RunReason.SETTINGS_CHANGE) } }
        // collectLatest is the debounce: a request that arrives while the previous one is still waiting, or still waiting
        // for a run to end, moves the start instead of adding a second run.
        scope.launch {
            scheduledRun.collectLatest { scheduled ->
                if (scheduled == null) return@collectLatest
                // A retry starts early once the device is back online: its wait is sized for an outage of unknown length.
                if (scheduled.isRetry) {
                    if (connectivityMonitor.awaitReconnection(-scheduled.dueAt.elapsedNow())) environment.logger.log("Sync: back online, retrying now")
                } else {
                    delay(-scheduled.dueAt.elapsedNow())
                }
                syncJob.load()?.join()
                if (scheduledRun.compareAndSet(scheduled, null)) {
                    environment.logger.log("Sync: starting the run asked for by ${scheduled.reason.description}")
                    startRun(SyncDeletionPolicy.ASK, isAutomatic = true)
                }
            }
        }
    }

    /**
     * Starts a run and returns; what it is doing and how it ended arrive through [SyncStateHolder.state]. Fire and forget
     * because the run outlives whoever asked for it - the screen that started it may be gone long before it
     * finishes, and on Android the activity may be too.
     *
     * The policy belongs to this one run rather than to the state: an answer to "delete them here too?" is an answer
     * about the files that were gone when it was asked, not a setting every later run should inherit.
     */
    fun synchronize(deletionPolicy: SyncDeletionPolicy) = startRun(deletionPolicy).also { isStarted ->
        // The run starts after every change an automatic one is waiting for, so it carries them too.
        if (isStarted) {
            scheduledRun.value = null
            networkFailureCount.store(0)
        }
    }

    /**
     * Asks for an automatic run [after] from now. It overwrites a waiting retry unconditionally: that is never
     * meaningfully earlier, and a change makes the run one the keep-alive should carry.
     *
     * @param after How long the library has to stay unchanged first. Zero for another device's change, which is already
     *   in the folder whole: the wait is for this device's edits, which come in bursts.
     * @param reason What asked for the run, which the log says once it starts: a burst of edits asks many times.
     */
    fun schedule(after: Duration = AUTOMATIC_RUN_DELAY, reason: RunReason) {
        if (stateHolder.value !is SyncState.Connected) return
        scheduledRun.value = ScheduledRun(dueAt = environment.timeSource.markNow() + after, isRetry = false, reason = reason)
    }

    /**
     * Starts an automatic run for the app coming back to the front, unless the last run was stopped or cut short, which
     * is reported rather than repeated, as at a launch, or is waiting for an answer about deletions, which Sync now and a
     * change to the library still ask again. It carries whatever an automatic run was waiting to carry.
     */
    fun synchronizeOnReturn() {
        val state = stateHolder.value as? SyncState.Connected ?: return
        // A question about deletions waits for its answer: asking it again would only take its rows off the screen for
        // the length of a run that stops at the same place.
        when {
            state.lastOutcome == SyncOutcome.Interrupted -> environment.logger.log("Sync: back in front, no run, the last one having been stopped")
            state.lastOutcome is SyncOutcome.DeletionsNeedConfirmation -> environment.logger.log("Sync: back in front, no run, a question about deletions being open")
            startRun(SyncDeletionPolicy.ASK, isAutomatic = true) -> {
                environment.logger.log("Sync: back in front, run started")
                scheduledRun.value = null
            }
            else -> environment.logger.log("Sync: back in front, a run already going")
        }
    }

    fun startScheduled(): SyncProgress? {
        val scheduled = scheduledRun.value
        when {
            scheduled == null -> Unit
            // A phone's keep-alive is for carrying a change the user just made; a retry started offline inside a
            // foreground service would only fail at once. It stays armed for the debounce, if the process lives.
            scheduled.isRetry && !scheduled.dueAt.hasPassedNow() -> Unit
            // The debounce chains the waiting run right behind the one that is going, as it would have a moment later.
            syncJob.load()?.isActive == true -> scheduledRun.compareAndSet(scheduled, scheduled.copy(dueAt = environment.timeSource.markNow()))
            // Started here rather than left to the debounce, which would start it on another thread a moment later:
            // the caller is the app leaving the front, and it can only hand a run to the platform's keep-alive while
            // it still is in front - which is what the progress this returns is for.
            scheduledRun.compareAndSet(scheduled, null) -> if (startRun(SyncDeletionPolicy.ASK, isAutomatic = true)) {
                environment.logger.log("Sync: leaving the front, starting the run asked for by ${scheduled.reason.description} for the keep-alive")
            } else {
                // A run started on another thread in between, and the one asked for now follows it.
                scheduledRun.compareAndSet(null, scheduled.copy(dueAt = environment.timeSource.markNow()))
            }
        }
        return (stateHolder.value as? SyncState.Connected)?.progress
    }

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    fun cancel() {
        environment.logger.log("Sync: stopped")
        // Cleared first, so that the debounce cannot start a run between the stop and the clear.
        scheduledRun.value = null
        networkFailureCount.store(0)
        syncJob.exchange(null)?.cancel()
    }

    /**
     * Starts a run unless one is going, and answers whether it did.
     *
     * @param isAutomatic Whether nobody asked for this run - the one [schedule] waits to start. It is
     *   written into the index's marker, see [SyncIndexDocument.isAutomaticRunInProgress].
     */
    private fun startRun(deletionPolicy: SyncDeletionPolicy, isAutomatic: Boolean = false): Boolean {
        val current = syncJob.load()
        if (current?.isActive == true) return false
        // Lazy, so that a run that lost the race below is dropped before it has done anything.
        val run = scope.launch(start = CoroutineStart.LAZY) {
            runner.run(deletionPolicy, isAutomatic)
            afterRun()
        }
        if (!syncJob.compareAndSet(current, run)) {
            run.cancel()
            return false
        }
        // Shown before the run gets to the thread it runs on, so that the state says a run is going by the time this
        // returns: see startScheduledSynchronization for the caller that depends on it. The run puts the same value
        // there again once it holds the lock, and clears it on every way out of its body - and the handler clears it
        // for a run that is cancelled before its body clears anything, waiting for the lock or not started yet.
        stateHolder.updateConnected { it.copy(progress = SyncProgress(), lastOutcome = null) }
        // Here as well as in the run, which only gets to it once it holds the lock on another thread: the watcher would
        // meanwhile report the changes this run is about to carry, the ones made while the app was away above all, and
        // have a second run follow it for nothing.
        runner.forgetWatchPoint()
        run.invokeOnCompletion { cause ->
            if (cause == null) return@invokeOnCompletion
            val current = syncJob.load()
            if (current == null || current === run || !current.isActive) {
                stateHolder.updateConnected { if (it.progress == null) it else it.copy(progress = null) }
            }
        }
        run.start()
        return true
    }

    /**
     * Arms a retry for a run the network failed, from inside that run's job: no other run can start before this returns,
     * so the outcome read here is that run's. A run Stop or Disconnect ended can still have recorded a network failure,
     * since a provider's exception can win over the cancellation that caused it, so a job no longer active arms nothing.
     * A disconnect leaves the state not Connected, which reads as nothing to retry.
     */
    private suspend fun afterRun() {
        if (!currentCoroutineContext().isActive) return
        val outcome = (stateHolder.value as? SyncState.Connected)?.lastOutcome
        if (outcome != SyncOutcome.Failure(SyncFailureReason.NETWORK)) {
            networkFailureCount.store(0)
            return
        }
        val count = networkFailureCount.incrementAndFetch()
        val wait = NETWORK_RETRY_DELAYS[minOf(count, NETWORK_RETRY_DELAYS.size) - 1]
        // Never over a waiting run: a change made during this one asked for an earlier start, and that run is the retry.
        if (scheduledRun.compareAndSet(null, ScheduledRun(dueAt = environment.timeSource.markNow() + wait, isRetry = true, reason = RunReason.RETRY))) {
            environment.logger.log("Sync: the network failed $count runs in a row, trying again in ${wait.inWholeMinutes} min or once back online")
        }
    }

    /**
     * Drops the waiting run and stops the one that is going, waiting for it to let go: what a disconnect needs, and an
     * authorization asked again for a connected account, which may end with another account behind the stored tokens.
     */
    suspend fun stopForConnectionChange() {
        environment.logger.log("Sync: runs dropped for a change of the connection")
        scheduledRun.value = null
        runner.forgetWatchPoint()
        networkFailureCount.store(0)
        syncJob.exchange(null)?.cancelAndJoin()
        // Again after the join, which waits for afterRun: a retry it armed in the instant before the cancellation is gone.
        scheduledRun.value = null
    }

    /**
     * An automatic run waiting to start, whether it only retries one the network failed rather than carries a change, and
     * what asked for it.
     */
    private data class ScheduledRun(val dueAt: ComparableTimeMark, val isRetry: Boolean, val reason: RunReason)

    /** What asked for an automatic run, as the log names it. */
    enum class RunReason(val description: String) {
        LIBRARY_CHANGE("a library change"),
        SETTINGS_CHANGE("a change of a synced setting"),
        CONNECTION("the connection"),
        OTHER_DEVICE("another device's change"),
        FEED_LOST("the change feed failing"),
        RETRY("a retry"),
    }

    internal companion object {
        /** How long the library has to stay unchanged before an automatic run starts, see [schedule]. */
        val AUTOMATIC_RUN_DELAY = 1.seconds

        /** The waits before each retry of a run the network failed, the last one repeated for as long as the app lives. */
        val NETWORK_RETRY_DELAYS: List<Duration> = listOf(1.minutes, 2.minutes, 5.minutes, 15.minutes, 30.minutes)
    }
}
