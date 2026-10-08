/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.SyncRunEndingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark

/**
 * Carries out one run at a time: the engine over the connected provider's folder, then `preferences.json`, with the
 * index written before anything moves, close behind the run and at its end, and the state told how it went.
 */
@Single
internal class SyncRunner(
    syncProviders: SyncProviders,
    private val engine: SyncEngine,
    private val syncedPreferencesSync: SyncedPreferencesSync,
    private val indexStore: SyncIndexStore,
    private val libraryRefresher: SyncLibraryRefresher,
    private val stateHolder: SyncStateHolder,
    private val environment: RepositoryEnvironment,
) {

    private val providers = syncProviders.all

    /** Nothing launched here may close the app, see [RepositoryEnvironment.scopeFor]. */
    private val scope = environment.scopeFor("sync")

    /** One run at a time: two of them over the same files would each undo half of what the other did. */
    private val mutex = Mutex()

    /** The periodic index write and when it was last made: of the runner rather than of a run, as they always were. */
    private var indexWriteJob: Job? = null
    private var lastIndexWrite: TimeMark? = null

    /** Runs [block] under the run lock, so that no run is halfway through anything while it goes. */
    suspend fun <T> withRunLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * The whole of a run, including the two index writes that open it: everything from the moment the progress is
     * put on screen has to be inside the try, because the only way off any other path is with the progress still
     * showing and no way left to stop or restart it. Stopping a run during those opening writes did exactly that.
     */
    suspend fun run(deletionPolicy: SyncDeletionPolicy, isAutomatic: Boolean) {
        // No check for the lock being taken: a run asked for while the previous one is still clearing up waits for
        // it rather than being dropped, which is what "stop, then start again" looks like from the settings screen.
        // Two runs at once are prevented by syncJob in startRun().
        mutex.withLock {
            val provider = try {
                providers.firstOrNull { it.isConnected() }
            } catch (exception: LibraryStorageException) {
                // Outside the run's own try below, so a failure here would reach the scope's handler with nothing on
                // screen. A successful read is cached, but a failed credentials write clears it for the next one.
                environment.logger.log("Could not read the stored sync credentials: ${exception.message}")
                stateHolder.updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.STORAGE)) }
                return@withLock
            } ?: run {
                // The credentials are gone while the screen still shows the account - a disconnect that did not get to
                // the end, a storage that lost them. Saying so is the only way the user gets a button that works.
                stateHolder.updateConnected { SyncState.ConnectionFailed(it.account.providerId, SyncFailureReason.AUTHORIZATION) }
                return@withLock
            }
            val connected = stateHolder.value as? SyncState.Connected ?: return@withLock
            stateHolder.update { connected.copy(progress = SyncProgress(), lastOutcome = null) }
            // What the engine has reported so far, which is what an interrupted run writes on its way out: the files
            // it did transfer stay known, and only the rest look unsynced next time.
            var latestIndex: (() -> SyncIndexDocument)? = null
            var hasFinishedOperations = false
            try {
                libraryRefresher.awaitFirstRead()
                // Taken over before the marker is written, so that an index filed under the key an earlier version used
                // is under the current one from the first write of this run, however the run ends.
                val document = indexStore.load().adoptedBy(connected.account)
                latestIndex = { document.markedAsRunning(isAutomatic) }
                // Written before anything moves, so that a run the app never comes back from is still recognisable
                // as interrupted next time - iOS suspending the app mid sync looks exactly like being killed.
                indexStore.save(document.markedAsRunning(isAutomatic))
                val result = engine.synchronize(
                    provider = provider,
                    document = document,
                    accountId = connected.account.indexKey(),
                    onProgress = { progress ->
                        stateHolder.updateConnected { it.copy(progress = progress) }
                        libraryRefresher.scheduleLiveRefresh()
                    },
                    onIndexChanged = { snapshot ->
                        val marked = { snapshot().markedAsRunning(isAutomatic) }
                        latestIndex = marked
                        hasFinishedOperations = true
                        scheduleIndexWrite(marked)
                    },
                    onLocalFileChanged = libraryRefresher::onFileChanged,
                    deletionPolicy = deletionPolicy,
                )
                indexWriteJob?.join()
                when (result) {
                    is SyncEngine.Result.DeletionsNeedConfirmation -> {
                        // Asked before the deletions moved, but not necessarily before anything did: a second pass
                        // can find the folder emptied after the first one had already brought files in, and those
                        // are on disk whether the question is answered.
                        indexStore.saveQuietly(latestIndex().markedAsFinished())
                        libraryRefresher.refreshAfterRun()
                        stateHolder.updateConnected {
                            it.copy(
                                progress = null,
                                lastOutcome = SyncOutcome.DeletionsNeedConfirmation(
                                    count = result.count,
                                    total = result.total,
                                    direction = result.direction,
                                ),
                            )
                        }
                    }

                    is SyncEngine.Result.Completed -> {
                        val syncedPreferences = synchronizePreferences(provider, result)
                        val summary = result.summary.copy(havePreferencesFailed = syncedPreferences == null)
                        // Only a run that moved everything it set out to move is one the two sides were in step after.
                        // One with failures keeps the time of the last run that was, which is also what "Last synced
                        // successfully" goes on saying while the next run is going.
                        val syncedAt = if (summary.isComplete) {
                            environment.clock.now().toEpochMilliseconds()
                        } else {
                            result.index.lastSyncedAt
                        }
                        indexStore.save(
                            result.index.copy(
                                lastSyncedAt = syncedAt,
                                syncedPreferences = syncedPreferences ?: result.index.syncedPreferences,
                            ),
                        )
                        // Reads only what the run changed, which for most runs is nothing.
                        libraryRefresher.refreshAfterRun()
                        stateHolder.updateConnected {
                            it.copy(
                                progress = null,
                                lastSyncedAt = syncedAt.takeIf { at -> at > 0 },
                                lastOutcome = SyncOutcome.Success(summary),
                            )
                        }
                    }
                }
            } catch (exception: CancellationException) {
                // Stopped rather than broken: nothing is wrong and nothing needs fixing, so this is its own outcome.
                withContext(NonCancellable) { finishRunCutShort(latestIndex) }
                stateHolder.updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Interrupted) }
                throw exception
            } catch (exception: Exception) {
                environment.logger.log("The sync run failed: ${exception.message}")
                withContext(NonCancellable) { finishRunCutShort(latestIndex) }
                stateHolder.updateConnected {
                    if (exception is SyncAuthorizationException) {
                        // Refused after a renewal was tried, so only a new authorization can answer it, and that
                        // is what ConnectionFailed offers. Kept Connected, the one way on would be Disconnect, which
                        // deletes the index and brings back everything deleted since the last run once the same
                        // account is connected again. Connecting keeps it (completePendingAuthorization).
                        SyncState.ConnectionFailed(it.account.providerId, SyncFailureReason.AUTHORIZATION)
                    } else {
                        it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason()))
                    }
                }
            } catch (throwable: Throwable) {
                // Not an Exception: what a synchronous js(...) call throws on the web (a JsException), or a real Error.
                // The run still owes everything a failed one owes, or the marker stays on disk and the lists keep the
                // old library. Not thrown on, since the scope's handler would only log it again.
                environment.logger.log("The sync run failed: $throwable")
                withContext(NonCancellable) {
                    finishRunCutShort(latestIndex)
                    // Thrown from anywhere, a write included, so what the engine reported is not necessarily all it
                    // changed: the whole library is read again.
                    if (hasFinishedOperations) libraryRefresher.rescanLibrary()
                }
                stateHolder.updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.UNKNOWN)) }
            } finally {
                // The one thing that has to be true on every way out, including any added later: no progress means
                // the settings screen offers to start a run again instead of offering to stop one that is over.
                stateHolder.updateConnected { if (it.progress == null) it else it.copy(progress = null) }
            }
        }
    }

    /**
     * Settles `preferences.json` once the library files have been, and answers the document to remember, or null where
     * it could not be settled. Only the failures that end a run end this one too: anything else is the one document's
     * problem, reported as [com.pandulapeter.campfire.data.model.domain.SyncSummary.havePreferencesFailed], and the
     * next run tries again.
     */
    private suspend fun synchronizePreferences(provider: SyncProvider, result: SyncEngine.Result.Completed) = try {
        syncedPreferencesSync.synchronize(
            provider = provider,
            base = result.index.syncedPreferences,
            keptFileNames = result.summary.failed,
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: SyncAuthorizationException) {
        throw exception
    } catch (exception: Exception) {
        environment.logger.log("Could not sync ${SyncedPreferencesDocument.FILE_NAME}: ${exception.message}")
        null
    }

    /**
     * Keeps `sync-index.json` close behind the run, so that an app that is killed rather than stopped - which gets no
     * chance to write anything on its way out - still loses no more than the last couple of seconds of transfers.
     * Throttled the same way as [SyncLibraryRefresher.scheduleLiveRefresh], since the index is rewritten whole and a run finishes a file
     * several times a second. Whatever this skips, the write at the end of the run covers.
     */
    private fun scheduleIndexWrite(snapshot: () -> SyncIndexDocument) {
        if (indexWriteJob?.isActive == true) return
        if (lastIndexWrite?.let { it.elapsedNow() < INDEX_WRITE_INTERVAL } == true) return
        lastIndexWrite = environment.timeSource.markNow()
        // Taken here and not in the job: the engine's lock is what makes reading its map safe, and it is only held
        // for as long as this call runs.
        val document = snapshot()
        indexWriteJob = scope.launch { indexStore.saveQuietly(document) }
    }

    /**
     * What a run that did not reach its end still owes: the periodic writer waited for, so that it cannot land after
     * the final write - waited for rather than cancelled, because on the web a cancelled write carries on in the
     * browser - the index as far as the run got without the marker saying one is going, and - where files may have
     * moved - the lists told about them. Without that last step the repositories keep the library from before the
     * run in their caches, and the next change made to a cached setlist or song text writes the old version back over
     * the one the run brought in.
     *
     * Always called inside [NonCancellable]: a stopped run is cancelled by definition, and a failed one may be - a
     * provider's exception can win over the cancellation that caused it - and in a cancelled coroutine the first
     * suspending call here would throw and skip the rest.
     */
    private suspend fun finishRunCutShort(latestIndex: (() -> SyncIndexDocument)?) {
        indexWriteJob?.join()
        latestIndex?.let { indexStore.saveQuietly(it().markedAsFinished()) }
        libraryRefresher.refreshAfterRun()
    }

    private companion object {
        /** How much of a run a killed app can lose at most, traded against rewriting the whole index per file. */
        val INDEX_WRITE_INTERVAL = 2.seconds
    }
}

internal fun Throwable.toFailureReason() = when (this) {
    is SyncRunEndingException -> reason
    is LibraryStorageException -> SyncFailureReason.STORAGE
    else -> SyncFailureReason.UNKNOWN
}
