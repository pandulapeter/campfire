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

package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncEngine
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncIndexDocument
import com.pandulapeter.campfire.data.repository.implementation.sync.indexKey
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.redirectParameters
import kotlinx.coroutines.cancelAndJoin
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * The state machine around [SyncEngine], and the only thing above the data layer that knows a service is involved
 * at all: the screens see a [SyncState], and which provider produced it is a detail of this file.
 *
 * @param syncProviders Every provider the build has. One is connected at a time - two would mean two remote folders
 *   with a claim on the same file names, and no answer to which of them a rename in one of them means.
 */
@Single
internal class SyncRepositoryImpl(
    syncProviders: SyncProviders,
    private val authenticator: SyncAuthenticator,
    private val pendingAuthorizationStore: PendingAuthorizationStore,
    private val syncStateLocalSource: SyncStateLocalSource,
    /**
     * Sync writes song and setlist files behind these two repositories' backs, so it is the one thing that has to
     * tell them to read the library again. It used to be the use case's job, back when a run finished before the
     * caller did; now that a run outlives whoever started it, this is the only place that reliably still exists
     * when it ends.
     */
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    libraryFileLocalSource: LibraryFileLocalSource,
) : SyncRepository {

    private val providers = syncProviders.all
    private val engine = SyncEngine(libraryFileLocalSource)
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Disconnected)
    override val syncState = _syncState.asStateFlow()
    override val availableProviders = providers.map { it.id }

    /**
     * A run belongs to the app, not to whatever screen started it. This repository is a singleton, so a sync
     * carries on while the user moves around the app, and on Android it survives the activity being destroyed -
     * which is what lets a foreground service keep it going after the app has been left.
     *
     * Nothing launched here has anybody to throw to, and an exception that leaves a job with no handler ends the
     * process on Android and iOS. Every job is written not to throw; the handler is there for the one that one day
     * does, because sync is something the app does on the side and must never be what closes it.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            println("A sync job ended in an exception nothing caught: $throwable")
        },
    )
    private var syncJob: Job? = null

    /** One run at a time: two of them over the same files would each undo half of what the other did. */
    private val mutex = Mutex()

    /**
     * Start up is asked for once per ViewModel, which on Android is once per activity rather than once per process,
     * and the second one may arrive while the first is still waiting for the service to say whose account this is.
     */
    private val restoreMutex = Mutex()

    private var liveRescanJob: Job? = null

    /** When the last live rescan ended and how long the next one has to wait for, see [scheduleLiveRescan]. */
    @Volatile
    private var lastLiveRescanEnd: TimeMark? = null

    @Volatile
    private var liveRescanPause = LIVE_RESCAN_INTERVAL

    private var indexWriteJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var lastIndexWrite: TimeMark? = null

    override suspend fun restore() = restoreMutex.withLock { restoreConnection() }

    private suspend fun restoreConnection(): SyncRepository.RestoreResult {
        // A consent page the app was sent away to, answered while it was not running - the web's ordinary case, and
        // Android's once the process was reclaimed behind the browser. Checked first because it decides what the
        // stored credentials are about to become. A redirect that no authorization is waiting for answers nothing -
        // Android hands a spent one over again when a finished task is reopened from the recents - and must not keep
        // the account that is connected from being restored below.
        val redirectUri = authenticator.consumePendingRedirect()
        if (redirectUri != null && pendingAuthorizationStore.loadPendingAuthorization() != null) {
            return SyncRepository.RestoreResult(
                isConnected = completePendingAuthorization(redirectUri),
                didReturnFromAuthorization = true,
                wasInterrupted = false,
            )
        }
        // Already answered in this process. The connection, a run that may be going and whatever the last one ended in
        // all live in the state, and reading them again from the disk would replace them with what the index said when
        // that run started - "a run is going", which read at start up means "a run was interrupted".
        (_syncState.value as? SyncState.Connected)?.let { connected ->
            return SyncRepository.RestoreResult(
                isConnected = true,
                didReturnFromAuthorization = false,
                wasInterrupted = !connected.isSyncing && connected.lastOutcome == SyncOutcome.Interrupted,
            )
        }
        val connected = providers.firstOrNull { it.isConnected() }
        if (connected == null) {
            _syncState.update { SyncState.Disconnected }
            return disconnectedResult
        }
        // What this device already knows about the account goes on screen before the service is asked anything: on a
        // bad network that answer can take over a minute, and until it came the settings screen would offer to connect
        // an account that is connected. Only a connection nothing was ever stored about has to wait for it.
        val storedAccount = connected.storedAccount()
        val account = storedAccount ?: try {
            connected.loadAccount()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Start up must never end in an exception because of a service: the library is what the app is for,
            // and sync is a thing it does on the side.
            println("Could not restore the ${connected.id} connection: ${exception.message}")
            null
        }
        if (account == null) {
            // The credentials are there but the service will not say who they belong to, which only happens once
            // they have been revoked. Nothing is deleted here: the user is told, and disconnecting is their call.
            _syncState.update { SyncState.ConnectionFailed(connected.id, SyncFailureReason.AUTHORIZATION) }
            return disconnectedResult
        }
        val document = loadIndexOrNull() ?: SyncIndexDocument()
        _syncState.update {
            SyncState.Connected(
                account = account,
                progress = null,
                lastSyncedAt = document.lastSyncedAt.takeIf { at -> at > 0 },
                // A run that was still marked as going when the app started is one the app never came back from.
                lastOutcome = if (document.isRunInProgress) SyncOutcome.Interrupted else null,
            )
        }
        if (document.isRunInProgress) {
            saveIndexQuietly(document.copy(isRunInProgress = false))
        }
        if (storedAccount != null) {
            refreshAccount(connected)
        }
        return SyncRepository.RestoreResult(
            isConnected = true,
            didReturnFromAuthorization = false,
            wasInterrupted = document.isRunInProgress,
        )
    }

    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean {
        val provider = providers.firstOrNull { it.id == providerId } ?: return false
        _syncState.update { SyncState.Connecting(providerId) }
        return try {
            val redirectUri = authenticator.prepareRedirectUri()
            val request = provider.buildAuthorizationRequest(redirectUri)
            // Written down before the browser is opened: on the web the app stops existing at the next line, and
            // the verifier still has to be there when it starts again.
            pendingAuthorizationStore.savePendingAuthorization(providerId, request)
            when (val outcome = authenticator.authorize(request.authorizationUrl, completionPage)) {
                is SyncAuthenticator.AuthorizationOutcome.Received -> completePendingAuthorization(outcome.redirectUri)
                // The app is on its way to the consent page; whatever it says arrives at the next start up.
                SyncAuthenticator.AuthorizationOutcome.Redirected -> false
                is SyncAuthenticator.AuthorizationOutcome.Cancelled -> {
                    println("The authorization was cancelled: ${outcome.message}")
                    discardPendingAuthorization()
                    // A message is the authenticator saying something went wrong on the way; without one, the user
                    // simply closed the page, which needs no explaining.
                    _syncState.update {
                        if (outcome.message == null) {
                            SyncState.Disconnected
                        } else {
                            SyncState.ConnectionFailed(providerId, SyncFailureReason.UNKNOWN)
                        }
                    }
                    false
                }
            }
        } catch (exception: CancellationException) {
            // The user gave up, which the UI offers while an authorization is waiting. The clean up still has to
            // happen, so it runs outside the cancellation before the exception carries on unswallowed.
            withContext(NonCancellable) { discardPendingAuthorization() }
            _syncState.update { SyncState.Disconnected }
            throw exception
        } catch (exception: Exception) {
            println("Could not connect to $providerId: ${exception.message}")
            discardPendingAuthorization()
            _syncState.update { SyncState.ConnectionFailed(providerId, exception.toFailureReason()) }
            false
        }
    }

    override suspend fun cancelConnection() {
        if (_syncState.value !is SyncState.Connecting) return
        discardPendingAuthorization()
        _syncState.update { if (it is SyncState.Connecting) SyncState.Disconnected else it }
    }

    /**
     * Carried to its end once it has started taking the connection apart: the credentials go first, and a disconnect
     * stopped after that would leave an account on screen that nothing can sync. What comes before - waiting for a
     * run to stop - can still be cancelled with the caller. The part that cannot is bounded by the provider's own time
     * limits on renewing and revoking the token.
     */
    override suspend fun disconnect() {
        // An answer still on its way belongs to the account that is about to go, and loadAccount writes the name it
        // reads into whatever credentials are stored by then - which could be the next account's.
        accountRefreshJob?.cancel()
        // A run that is still going would carry on against an account that is gone, fail, and report that failure
        // onto an account the user just disconnected.
        syncJob?.cancelAndJoin()
        syncJob = null
        withContext(NonCancellable) {
            providers.forEach { provider ->
                if (provider.isConnected()) {
                    try {
                        provider.disconnect()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        println("Could not disconnect from ${provider.id}: ${exception.message}")
                    }
                }
            }
            // The index describes a remote folder this device is no longer looking at. Kept, and it would read that
            // folder's every file as a deletion the next time something connects. Under the run lock, because a run
            // that was stopped a moment ago is not in syncJob any more and may still be writing the index on its way
            // out; no new one can slip in, since the providers above no longer say they are connected.
            mutex.withLock {
                syncStateLocalSource.saveSyncIndex(null)
                _syncState.update { SyncState.Disconnected }
            }
        }
    }

    /**
     * Starts a run and returns; what it is doing and how it ended arrive through [syncState]. Fire and forget
     * because the run outlives whoever asked for it - the screen that started it may be gone long before it
     * finishes, and on Android the activity may be too.
     *
     * The policy belongs to this one run rather than to the state: an answer to "delete them here too?" is an answer
     * about the files that were gone when it was asked, not a setting every later run should inherit.
     */
    override fun synchronize(deletionPolicy: SyncDeletionPolicy) {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch { runSynchronization(deletionPolicy) }
    }

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    override fun cancelSynchronization() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * The whole of a run, including the two index writes that open it: everything from the moment the progress is
     * put on screen has to be inside the try, because the only way off any other path is with the progress still
     * showing and no way left to stop or restart it. Stopping a run during those opening writes did exactly that.
     */
    private suspend fun runSynchronization(deletionPolicy: SyncDeletionPolicy) {
        // No check for the lock being taken: a run asked for while the previous one is still clearing up waits for
        // it rather than being dropped, which is what "stop, then start again" looks like from the settings screen.
        // Two runs at once are prevented by syncJob in synchronize().
        mutex.withLock {
            val provider = providers.firstOrNull { it.isConnected() } ?: run {
                // The credentials are gone while the screen still shows the account - a disconnect that did not get to
                // the end, a storage that lost them. Saying so is the only way the user gets a button that works.
                updateConnected { SyncState.ConnectionFailed(it.account.providerId, SyncFailureReason.AUTHORIZATION) }
                return@withLock
            }
            val connected = _syncState.value as? SyncState.Connected ?: return@withLock
            _syncState.update { connected.copy(progress = SyncProgress(), lastOutcome = null) }
            // What the engine has reported so far, which is what an interrupted run writes on its way out: the files
            // it did transfer stay known, and only the rest look unsynced next time.
            var latestIndex: (() -> SyncIndexDocument)? = null
            var hasFinishedOperations = false
            try {
                // Taken over before the marker is written, so that an index filed under the key an earlier version used
                // is under the current one from the first write of this run, however the run ends.
                val document = loadIndex().adoptedBy(connected.account)
                latestIndex = { document.copy(isRunInProgress = true) }
                // Written before anything moves, so that a run the app never comes back from is still recognisable
                // as interrupted next time - iOS suspending the app mid sync looks exactly like being killed.
                saveIndex(document.copy(isRunInProgress = true))
                val result = engine.synchronize(
                    provider = provider,
                    document = document,
                    accountId = connected.account.indexKey(),
                    onProgress = { progress ->
                        updateConnected { it.copy(progress = progress) }
                        scheduleLiveRescan()
                    },
                    onIndexChanged = { snapshot ->
                        latestIndex = snapshot
                        hasFinishedOperations = true
                        scheduleIndexWrite(snapshot)
                    },
                    deletionPolicy = deletionPolicy,
                )
                indexWriteJob?.join()
                when (result) {
                    is SyncEngine.Result.DeletionsNeedConfirmation -> {
                        // Asked before the deletions moved, but not necessarily before anything did: a second pass
                        // can find the folder emptied after the first one had already brought files in, and those
                        // are on disk whether or not the question is answered.
                        latestIndex?.let { saveIndexQuietly(it().copy(isRunInProgress = false)) }
                        if (hasFinishedOperations) {
                            rescanLibraryAfterRun()
                        }
                        updateConnected {
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
                        // Only a run that moved everything it set out to move is one the two sides were in step after.
                        // One with failures keeps the time of the last run that was, which is also what "Last synced
                        // successfully" goes on saying while the next run is going.
                        val syncedAt = if (result.summary.failed.isEmpty()) {
                            Clock.System.now().toEpochMilliseconds()
                        } else {
                            result.index.lastSyncedAt
                        }
                        saveIndex(result.index.copy(lastSyncedAt = syncedAt))
                        // Only when something actually moved: most runs find nothing to do, and re-reading the whole
                        // library every time the app is opened would cost more than the sync itself.
                        if (result.summary.hasChanges) {
                            rescanLibraryAfterRun()
                        }
                        updateConnected {
                            it.copy(
                                progress = null,
                                lastSyncedAt = syncedAt.takeIf { at -> at > 0 },
                                lastOutcome = SyncOutcome.Success(result.summary),
                            )
                        }
                    }
                }
            } catch (exception: CancellationException) {
                // Stopped rather than broken: nothing is wrong and nothing needs fixing, so this is its own outcome.
                withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Interrupted) }
                throw exception
            } catch (exception: Exception) {
                println("The sync run failed: ${exception.message}")
                withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
                updateConnected {
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
                println("The sync run failed: $throwable")
                withContext(NonCancellable) { finishRunCutShort(latestIndex, hasFinishedOperations) }
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.UNKNOWN)) }
            } finally {
                // The one thing that has to be true on every way out, including any added later: no progress means
                // the settings screen offers to start a run again instead of offering to stop one that is over.
                updateConnected { if (it.progress == null) it else it.copy(progress = null) }
            }
        }
    }

    /**
     * Keeps the library counts moving while a run is going.
     *
     * Sync writes files behind the two repositories' backs, so nothing reads them again until something says to -
     * and until this, that was only the rescan at the end of a run, which left the counters still until it finished.
     * Throttled by what the last one cost, see [liveRescanPauseAfter]: there is no per file way into the cache, so one
     * per file would re-read everything a few thousand times over a single run. The exact numbers still come from the
     * run's own rescan when it ends; this only keeps them moving on the way there.
     */
    private fun scheduleLiveRescan() {
        if (liveRescanJob?.isActive == true) return
        if (lastLiveRescanEnd?.let { it.elapsedNow() < liveRescanPause } == true) return
        liveRescanJob = scope.launch {
            val duration = measureTime { rescanLibrary() }
            liveRescanPause = liveRescanPauseAfter(duration)
            lastLiveRescanEnd = TimeSource.Monotonic.markNow()
        }
    }

    /**
     * Keeps `sync-index.json` close behind the run, so that an app that is killed rather than stopped - which gets no
     * chance to write anything on its way out - still loses no more than the last couple of seconds of transfers.
     * Throttled the same way as [scheduleLiveRescan], since the index is rewritten whole and a run finishes a file
     * several times a second. Whatever this skips, the write at the end of the run covers.
     */
    private fun scheduleIndexWrite(snapshot: () -> SyncIndexDocument) {
        if (indexWriteJob?.isActive == true) return
        if (lastIndexWrite?.let { it.elapsedNow() < INDEX_WRITE_INTERVAL } == true) return
        lastIndexWrite = TimeSource.Monotonic.markNow()
        // Taken here and not in the job: the engine's lock is what makes reading its map safe, and it is only held
        // for as long as this call runs.
        val document = snapshot()
        indexWriteJob = scope.launch { saveIndexQuietly(document) }
    }

    /**
     * Asks the service who the account is behind a start up that has already shown what was stored. The answer only
     * ever changes a state that is still connected to the same provider: a new name replaces the stored one, and a
     * refusal - the grant was revoked elsewhere - takes the connection down the way a start up that waited would
     * have. Anything else, a dead network above all, leaves what is on screen alone.
     */
    private fun refreshAccount(provider: SyncProvider) {
        if (accountRefreshJob?.isActive == true) return
        accountRefreshJob = scope.launch {
            val account = try {
                provider.loadAccount()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not refresh the ${provider.id} account: ${exception.message}")
                return@launch
            }
            updateConnected { connected ->
                when {
                    connected.account.providerId != provider.id -> connected
                    account == null -> SyncState.ConnectionFailed(provider.id, SyncFailureReason.AUTHORIZATION)
                    else -> connected.copy(account = account)
                }
            }
        }
    }

    /**
     * Changes the state only while it is still [SyncState.Connected]. A run reports how it went when it ends, and if
     * the account was disconnected in the meantime that report has nothing to attach itself to: rebuilding the
     * connected state from what the run remembers would put the account back on screen.
     */
    private inline fun updateConnected(transform: (SyncState.Connected) -> SyncState) = _syncState.update {
        if (it is SyncState.Connected) transform(it) else it
    }

    private suspend fun rescanLibrary() {
        songRepository.rescan()
        setlistRepository.rescan()
    }

    /**
     * The rescan a run ends with. A live one still reading is stopped first: it started before the last files moved,
     * so this one has to follow it anyway, and would only wait for it. A cancelled read leaves the cached data as it
     * was (see `BaseLocalDataRepository`).
     */
    private suspend fun rescanLibraryAfterRun() {
        liveRescanJob?.cancelAndJoin()
        rescanLibrary()
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
    private suspend fun finishRunCutShort(latestIndex: (() -> SyncIndexDocument)?, hasFinishedOperations: Boolean) {
        indexWriteJob?.join()
        latestIndex?.let { saveIndexQuietly(it().copy(isRunInProgress = false)) }
        // Only when an operation got as far as finishing: a run that fails on its listing - every launch without a
        // network - has moved nothing, and reading a whole library again for it would cost more than the run did.
        if (hasFinishedOperations) {
            rescanLibraryAfterRun()
        }
    }

    /**
     * Forgets the authorization that was started, for the ways out that have already decided how they end. Clearing
     * writes the credentials document, and a storage that refuses that write must not replace the ending with an
     * exception of its own: [connect] runs in a launched coroutine with nobody to throw to, and the state has to
     * leave [SyncState.Connecting] whatever the storage says. What stays behind is a verifier nothing asks for
     * again, and the next authorization writes over it.
     */
    private suspend fun discardPendingAuthorization() = try {
        pendingAuthorizationStore.clearPendingAuthorization()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // Only the kind of failure: the message of one that came from the credentials document may quote it.
        println("Could not clear the pending authorization: ${exception::class.simpleName}")
    }

    /**
     * Turns a redirect into a connection. Shared by the platforms that come back to a running app and the one that
     * comes back to a new one, so that both take exactly the same path through the token exchange.
     */
    private suspend fun completePendingAuthorization(redirectUri: String): Boolean {
        val pending = pendingAuthorizationStore.loadPendingAuthorization()
        if (pending == null) {
            println("A redirect arrived that no authorization was waiting for.")
            _syncState.update { SyncState.Disconnected }
            return false
        }
        pendingAuthorizationStore.clearPendingAuthorization()
        val provider = providers.firstOrNull { it.id == pending.providerId }
        val parameters = redirectParameters(redirectUri)
        val code = parameters["code"]
        val state = parameters["state"]
        return when {
            provider == null -> fail(
                providerId = pending.providerId,
                reason = SyncFailureReason.UNKNOWN,
                message = "The redirect names a provider this build does not have.",
            )
            code == null -> fail(
                providerId = pending.providerId,
                reason = SyncFailureReason.AUTHORIZATION,
                message = "The service refused the authorization: ${parameters["error"].orEmpty()}",
            )
            // The value the app generated has to come back untouched, or this redirect was not asked for by it.
            state != pending.state -> fail(
                providerId = pending.providerId,
                reason = SyncFailureReason.UNKNOWN,
                message = "The redirect does not belong to the authorization that was started.",
            )
            else -> try {
                val account = provider.completeAuthorization(
                    response = RemoteAuthorizationResponse(code = code, state = state),
                    verifier = pending.verifier,
                    redirectUri = pending.redirectUri,
                )
                // The account decides which remote folder the index describes, so one written for a different
                // account is worthless rather than merely stale. One that cannot be read is left for the run to find:
                // replaced here, it could be the good index of this very account.
                val document = loadIndexOrNull()?.adoptedBy(account)
                if (document != null && document.accountId != account.indexKey()) {
                    saveIndex(SyncIndexDocument())
                }
                _syncState.update {
                    SyncState.Connected(account = account, progress = null, lastSyncedAt = null, lastOutcome = null)
                }
                true
            } catch (exception: CancellationException) {
                // Giving up during the token exchange is not the exchange failing: connect() answers it by going back
                // to Disconnected, which it can only do if this arrives there as a cancellation.
                throw exception
            } catch (exception: Exception) {
                fail(
                    providerId = pending.providerId,
                    reason = exception.toFailureReason(),
                    message = "The authorization could not be completed: ${exception.message}",
                )
            }
        }
    }

    private fun fail(providerId: SyncProviderId, reason: SyncFailureReason, message: String): Boolean {
        println(message)
        _syncState.update { SyncState.ConnectionFailed(providerId, reason) }
        return false
    }

    private fun Throwable.toFailureReason() = when (this) {
        is SyncAuthorizationException -> SyncFailureReason.AUTHORIZATION
        is SyncNetworkException -> SyncFailureReason.NETWORK
        is SyncRemoteStorageFullException -> SyncFailureReason.REMOTE_STORAGE_FULL
        is LibraryStorageException -> SyncFailureReason.STORAGE
        else -> SyncFailureReason.UNKNOWN
    }

    // Documents

    /**
     * Throws when the file is there and cannot be read: an index taken for none is a run that undoes every deletion
     * since the last one, and then writes the empty index over the good one. One that reads but does not decode is
     * worth nothing to anybody and starts from nothing, as a device that never synced does.
     */
    private suspend fun loadIndex(): SyncIndexDocument {
        val text = syncStateLocalSource.loadSyncIndex() ?: return SyncIndexDocument()
        return try {
            json.decodeFromString<SyncIndexDocument>(text)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not decode the sync index: ${exception.message}")
            SyncIndexDocument()
        }
    }

    /** For the callers that only show what the index says or check whose it is, and must not throw because of it. */
    private suspend fun loadIndexOrNull() = try {
        loadIndex()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the sync index: ${exception.message}")
        null
    }

    private suspend fun saveIndex(document: SyncIndexDocument) = syncStateLocalSource.saveSyncIndex(json.encodeToString(document))

    /**
     * For the writes nobody is waiting on the result of - the periodic one and the ones made on the way out of a run.
     * The index only ever saves work: whatever it fails to record looks unsynced to the next run, which is a slower
     * run and not a wrong one, so a failure here is not worth more than a line in the log. A run whose storage is
     * really gone still says so, through the opening write and the one that completes it.
     */
    private suspend fun saveIndexQuietly(document: SyncIndexDocument) = try {
        saveIndex(document)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not write the sync index: ${exception.message}")
    }

    private companion object {
        /** How much of a run a killed app can lose at most, traded against rewriting the whole index per file. */
        val INDEX_WRITE_INTERVAL = 2.seconds

        val disconnectedResult = SyncRepository.RestoreResult(
            isConnected = false,
            didReturnFromAuthorization = false,
            wasInterrupted = false,
        )
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}

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
