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

package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.implementation.LibraryChanges
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

/**
 * When a run starts: at once when it is asked for, or - for the automatic one every change asks for - once the library
 * has stayed unchanged for [AUTOMATIC_RUN_DELAY], and never while another is going.
 */
@Single
internal class SyncRunScheduler(
    private val runner: SyncRunner,
    private val stateHolder: SyncStateHolder,
    libraryChanges: LibraryChanges,
    syncedPreferencesSync: SyncedPreferencesSync,
    private val environment: RepositoryEnvironment,
) {

    /**
     * A run belongs to the app, not to whatever screen started it. This scheduler is a singleton, so a sync
     * carries on while the user moves around the app, and on Android it survives the activity being destroyed -
     * which is what lets a foreground service keep it going after the app has been left.
     *
     * Nothing launched here may close the app, see [RepositoryEnvironment.scopeFor]: sync is something the app does
     * on the side.
     */
    private val scope = environment.scopeFor("sync")

    /**
     * The run that is going, or the last one. Swapped atomically rather than assigned: the buttons start and stop runs
     * from the main thread, while an automatic run is started from this scope's own, and two starters that both found
     * no run going would each start one, the second one then being the only one Stop can reach.
     */
    private val syncJob = AtomicReference<Job?>(null)

    /** When the automatic run [schedule] asked for is to start, or null while none is waiting. */
    private val scheduledRunDueAt = MutableStateFlow<TimeMark?>(null)

    init {
        scope.launch { libraryChanges.changes.collect { schedule() } }
        scope.launch { syncedPreferencesSync.localChanges.collect { schedule() } }
        // collectLatest is the debounce: a request that arrives while the previous one is still waiting, or still waiting
        // for a run to end, moves the start instead of adding a second run.
        scope.launch {
            scheduledRunDueAt.collectLatest { dueAt ->
                if (dueAt == null) return@collectLatest
                delay(-dueAt.elapsedNow())
                syncJob.load()?.join()
                if (scheduledRunDueAt.compareAndSet(dueAt, null)) startRun(SyncDeletionPolicy.ASK, isAutomatic = true)
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
        if (isStarted) scheduledRunDueAt.value = null
    }

    fun schedule() {
        if (stateHolder.value !is SyncState.Connected) return
        scheduledRunDueAt.value = environment.timeSource.markNow() + AUTOMATIC_RUN_DELAY
    }

    fun startScheduled(): SyncProgress? {
        val dueAt = scheduledRunDueAt.value
        when {
            dueAt == null -> Unit
            // The debounce chains the waiting run right behind the one that is going, as it would have in ten seconds.
            syncJob.load()?.isActive == true -> scheduledRunDueAt.compareAndSet(dueAt, environment.timeSource.markNow())
            // Started here rather than left to the debounce, which would start it on another thread a moment later:
            // the caller is the app leaving the front, and it can only hand a run to the platform's keep-alive while
            // it still is in front - which is what the progress this returns is for.
            scheduledRunDueAt.compareAndSet(dueAt, null) -> if (!startRun(SyncDeletionPolicy.ASK, isAutomatic = true)) {
                // A run started on another thread in between, and the one asked for now follows it.
                scheduledRunDueAt.compareAndSet(null, environment.timeSource.markNow())
            }
        }
        return (stateHolder.value as? SyncState.Connected)?.progress
    }

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    fun cancel() {
        scheduledRunDueAt.value = null
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
        val run = scope.launch(start = CoroutineStart.LAZY) { runner.run(deletionPolicy, isAutomatic) }
        if (!syncJob.compareAndSet(current, run)) {
            run.cancel()
            return false
        }
        // Shown before the run gets to the thread it runs on, so that the state says a run is going by the time this
        // returns: see startScheduledSynchronization for the caller that depends on it. The run puts the same value
        // there again once it holds the lock, and clears it on every way out of its body - and the handler clears it
        // for a run that is cancelled before its body clears anything, waiting for the lock or not started yet.
        stateHolder.updateConnected { it.copy(progress = SyncProgress(), lastOutcome = null) }
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

    /** Drops the waiting run and stops the one that is going, waiting for it to let go: what a disconnect needs. */
    suspend fun stopForDisconnect() {
        scheduledRunDueAt.value = null
        syncJob.exchange(null)?.cancelAndJoin()
    }

    private companion object {
        /** How long the library has to stay unchanged before an automatic run starts, see [schedule]. */
        val AUTOMATIC_RUN_DELAY = 10.seconds
    }
}
