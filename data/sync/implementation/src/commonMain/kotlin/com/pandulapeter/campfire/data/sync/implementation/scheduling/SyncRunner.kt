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

package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexStore
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.index.indexKey
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedCopySync
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksFormat
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
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
    private val sharedCopySync: SharedCopySync,
    private val indexStore: SyncIndexStore,
    private val libraryRefresher: SyncLibraryRefresher,
    private val stateHolder: SyncStateHolder,
    private val environment: SyncEnvironment,
) {

    private val providers = syncProviders.all

    /** Nothing launched here may close the app, see [SyncEnvironment.scopeFor]. */
    private val scope = environment.scopeFor("sync")

    /** One run at a time: two of them over the same files would each undo half of what the other did. */
    private val mutex = Mutex()

    /** The periodic index write and when it was last made: of the runner rather than of a run, as they always were. */
    private var indexWriteJob: Job? = null
    private var lastIndexWrite: TimeMark? = null

    private val _watchPoint = MutableStateFlow<SyncWatchPoint?>(null)

    /**
     * Where [SyncRemoteWatcher] watches the folder from: set by a run that completed, and cleared as soon as one starts,
     * since its own writes would read as another device's, and by a disconnect. Any other ending leaves it cleared, which
     * keeps a run the user stopped, or one waiting for an answer about deletions, from being started again by the feed.
     */
    val watchPoint = _watchPoint.asStateFlow()

    fun forgetWatchPoint() {
        _watchPoint.value = null
    }

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
            environment.logger.log("Sync: run started (${if (isAutomatic) "automatic" else "asked for"}, $deletionPolicy)")
            _watchPoint.value = null
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
                        // From here on a Stop must never write an index older than the one the engine returned, nor,
                        // once the preferences are written here, one whose preferences base predates them.
                        latestIndex = { result.index }
                        // The index loaded at the start, not the engine's: only it pairs the base with the revision
                        // the folder had for it, which the engine's rebuilt index leaves null.
                        val unchanged = document.unchangedPreferences(connected.account.indexKey(), result.documentRevisions)
                        val settled = synchronizePreferences(provider, result, unchanged) { base ->
                            latestIndex = { result.index.copy(syncedPreferences = base) }
                        }
                        // Before the index is saved and the watch point set, so that a run this step ends hands the feed no point.
                        val areSharedCopiesSettled = synchronizeSharedCopies(provider, result)
                        val summary = result.summary.copy(
                            havePreferencesFailed = settled == null,
                            haveSharedCopiesFailed = !areSharedCopiesSettled,
                        )
                        // Only a run that moved everything it set out to move is one the two sides were in step after.
                        // One with failures keeps the time of the last run that was, which is also what "Last synced
                        // successfully" goes on saying while the next run is going.
                        val syncedAt = if (summary.isComplete) {
                            environment.clock.now().toEpochMilliseconds()
                        } else {
                            result.index.lastSyncedAt
                        }
                        val completed = result.index.copy(
                            lastSyncedAt = syncedAt,
                            syncedPreferences = settled?.document ?: result.index.syncedPreferences,
                            syncedPreferencesRevision = settled?.revision,
                        )
                        latestIndex = { completed }
                        indexStore.save(completed)
                        _watchPoint.value = result.cursor?.let { cursor ->
                            SyncWatchPoint(
                                folder = provider,
                                cursor = cursor,
                                index = completed.toIndex(),
                                syncedPreferences = completed.syncedPreferences,
                            )
                        }
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
                environment.logger.log("Sync: run ended (${describe((stateHolder.value as? SyncState.Connected)?.lastOutcome)})")
            }
        }
    }

    /**
     * Settles `preferences.json` once the library files have been, and answers the document to remember with the folder's
     * revision of it, or null where it could not be settled; [unchanged] stands in for the first download, see
     * [SyncedPreferencesSync.synchronize]. A failure that ends a run (refused, the network, a full folder) ends this one with its
     * reason: anything else is the one document's problem, reported as [com.pandulapeter.campfire.data.model.sync.SyncSummary.havePreferencesFailed], and the
     * next run tries again.
     */
    private suspend fun synchronizePreferences(
        provider: SyncProvider,
        result: SyncEngine.Result.Completed,
        unchanged: RemoteDocument?,
        onLocalSettled: (JsonObject?) -> Unit,
    ) = try {
        syncedPreferencesSync.synchronize(
            provider = provider,
            base = result.index.syncedPreferences,
            unchanged = unchanged,
            keptFileNames = result.summary.failed,
            onLocalSettled = onLocalSettled,
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: SyncRunEndingException) {
        throw exception
    } catch (exception: Exception) {
        environment.logger.log("Could not sync ${SyncedPreferencesDocument.FILE_NAME}: ${exception.message}")
        null
    }

    /**
     * Brings the shared links' copies up to date with the library the run left, see [SharedCopySync], and answers whether
     * every one was. A failure that ends a run ends this one with its reason, so that one cut short by the network is
     * retried like any other; anything else, a connection made before sharing was asked for included, is reported as
     * [com.pandulapeter.campfire.data.model.sync.SyncSummary.haveSharedCopiesFailed] and never fails the run.
     */
    private suspend fun synchronizeSharedCopies(provider: SyncProvider, result: SyncEngine.Result.Completed) = try {
        sharedCopySync.synchronize(
            folder = provider,
            index = result.index.toIndex(),
            sharedFiles = result.sharedFiles,
            registryRevision = result.documentRevisions[SharedLinksFormat.NAME],
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: SyncRunEndingException) {
        throw exception
    } catch (exception: Exception) {
        environment.logger.log("Could not update the shared copies: ${exception::class.simpleName}")
        false
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

/** A run's ending in a few words for the process log: counts and reasons, never the names of the files involved. */
private fun describe(outcome: SyncOutcome?) = when (outcome) {
    null -> "no outcome"
    is SyncOutcome.Success -> with(outcome.summary) {
        "completed: $downloaded downloaded, $uploaded uploaded, ${deletedLocally + deletedRemotely} deleted, ${conflicts.size} conflicts, " +
            "${failed.size} failed${if (havePreferencesFailed) ", preferences failed" else ""}${if (haveSharedCopiesFailed) ", shared copies failed" else ""}"
    }
    is SyncOutcome.DeletionsNeedConfirmation -> "asks about ${outcome.count} deletions"
    SyncOutcome.Interrupted -> "stopped"
    is SyncOutcome.Failure -> "failed, ${outcome.reason}"
}

internal fun Throwable.toFailureReason() = when (this) {
    is SyncRunEndingException -> reason
    is LibraryStorageException -> SyncFailureReason.STORAGE
    else -> SyncFailureReason.UNKNOWN
}
