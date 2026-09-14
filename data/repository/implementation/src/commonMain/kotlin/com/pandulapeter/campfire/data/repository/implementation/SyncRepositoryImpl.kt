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

import com.pandulapeter.campfire.data.model.domain.SyncAccount
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
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.redirectParameters
import kotlinx.coroutines.cancelAndJoin
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
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
 * @param providers Every provider the build has. One is connected at a time - two would mean two remote folders
 *   with a claim on the same file names, and no answer to which of them a rename in one of them means.
 */
@Single
internal class SyncRepositoryImpl(
    private val providers: List<SyncProvider>,
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

    private val engine = SyncEngine(libraryFileLocalSource)
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Disconnected)
    override val syncState = _syncState.asStateFlow()
    override val availableProviders = providers.map { it.id }

    /**
     * A run belongs to the app, not to whatever screen started it. This repository is a singleton, so a sync
     * carries on while the user moves around the app, and on Android it survives the activity being destroyed -
     * which is what lets a foreground service keep it going after the app has been left.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null

    /** One run at a time: two of them over the same files would each undo half of what the other did. */
    private val mutex = Mutex()

    private var liveRescanJob: Job? = null
    private var lastLiveRescanAt = 0L

    private var indexWriteJob: Job? = null
    private var lastIndexWriteAt = 0L

    override suspend fun restore(): SyncRepository.RestoreResult {
        // A consent page the app was sent away to, answered while it was not running - the web's ordinary case, and
        // checked first because it decides what the stored credentials are about to become.
        authenticator.consumePendingRedirect()?.let { redirectUri ->
            return SyncRepository.RestoreResult(
                isConnected = completePendingAuthorization(redirectUri),
                didReturnFromAuthorization = true,
                wasInterrupted = false,
            )
        }
        val connected = providers.firstOrNull { it.isConnected() }
        if (connected == null) {
            _syncState.update { SyncState.Disconnected }
            return disconnectedResult
        }
        val account = try {
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
        val document = loadIndex()
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
            saveIndex(document.copy(isRunInProgress = false))
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
                    pendingAuthorizationStore.clearPendingAuthorization()
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
            withContext(NonCancellable) { pendingAuthorizationStore.clearPendingAuthorization() }
            _syncState.update { SyncState.Disconnected }
            throw exception
        } catch (exception: Exception) {
            println("Could not connect to $providerId: ${exception.message}")
            pendingAuthorizationStore.clearPendingAuthorization()
            _syncState.update { SyncState.ConnectionFailed(providerId, exception.toFailureReason()) }
            false
        }
    }

    override suspend fun disconnect() {
        // A run that is still going would carry on against an account that is gone, fail, and report that failure
        // onto an account the user just disconnected.
        syncJob?.cancelAndJoin()
        syncJob = null
        providers.forEach { provider ->
            if (provider.isConnected()) {
                try {
                    provider.disconnect()
                } catch (exception: Exception) {
                    println("Could not disconnect from ${provider.id}: ${exception.message}")
                }
            }
        }
        // The index describes a remote folder this device is no longer looking at. Kept, and it would read that
        // folder's every file as a deletion the next time something connects.
        syncStateLocalSource.saveSyncIndex(null)
        _syncState.update { SyncState.Disconnected }
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
            val provider = providers.firstOrNull { it.isConnected() } ?: return@withLock
            val connected = _syncState.value as? SyncState.Connected ?: return@withLock
            _syncState.update { connected.copy(progress = SyncProgress(), lastOutcome = null) }
            // What the engine has reported so far, which is what an interrupted run writes on its way out: the files
            // it did transfer stay known, and only the rest look unsynced next time.
            var latestIndex: SyncIndexDocument? = null
            try {
                val document = loadIndex()
                latestIndex = document.copy(isRunInProgress = true)
                // Written before anything moves, so that a run the app never comes back from is still recognisable
                // as interrupted next time - iOS suspending the app mid sync looks exactly like being killed.
                saveIndex(document.copy(isRunInProgress = true))
                val result = engine.synchronize(
                    provider = provider,
                    document = document,
                    accountId = accountIdOf(connected.account),
                    onProgress = { progress ->
                        updateConnected { it.copy(progress = progress) }
                        scheduleLiveRescan()
                    },
                    onIndexChanged = {
                        latestIndex = it
                        scheduleIndexWrite(it)
                    },
                    deletionPolicy = deletionPolicy,
                )
                indexWriteJob?.cancelAndJoin()
                when (result) {
                    is SyncEngine.Result.DeletionsNeedConfirmation -> {
                        // The run stopped before anything moved, so there is nothing for the lists to read again and
                        // the index only has to stop saying that a run is going.
                        latestIndex?.let { saveIndex(it.copy(isRunInProgress = false)) }
                        updateConnected {
                            it.copy(
                                progress = null,
                                lastOutcome = SyncOutcome.DeletionsNeedConfirmation(count = result.count, total = result.total),
                            )
                        }
                    }

                    is SyncEngine.Result.Completed -> {
                        val syncedAt = Clock.System.now().toEpochMilliseconds()
                        saveIndex(result.index.copy(lastSyncedAt = syncedAt))
                        // Only when something actually moved: most runs find nothing to do, and re-reading the whole
                        // library every time the app is opened would cost more than the sync itself.
                        if (result.summary.hasChanges) {
                            rescanLibrary()
                        }
                        updateConnected {
                            it.copy(
                                progress = null,
                                lastSyncedAt = syncedAt,
                                lastOutcome = SyncOutcome.Success(result.summary),
                            )
                        }
                    }
                }
            } catch (exception: CancellationException) {
                // Stopped rather than broken: nothing is wrong and nothing needs fixing, so this is its own outcome.
                // Whatever did move before the stop is on disk, so the lists still have to be told about it.
                withContext(NonCancellable) {
                    indexWriteJob?.cancelAndJoin()
                    latestIndex?.let { saveIndex(it.copy(isRunInProgress = false)) }
                    rescanLibrary()
                }
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Interrupted) }
                throw exception
            } catch (exception: Exception) {
                println("The sync run failed: ${exception.message}")
                indexWriteJob?.cancelAndJoin()
                latestIndex?.let { saveIndex(it.copy(isRunInProgress = false)) }
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(exception.toFailureReason())) }
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
     * Throttled because a rescan is a read of the whole library and there is no per file way into the cache: one per
     * file would re-read everything a few hundred times over a single run. The exact numbers still come from the
     * run's own rescan when it ends; this only keeps them moving on the way there.
     */
    private fun scheduleLiveRescan() {
        if (liveRescanJob?.isActive == true) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastLiveRescanAt < LIVE_RESCAN_INTERVAL_MS) return
        lastLiveRescanAt = now
        liveRescanJob = scope.launch { rescanLibrary() }
    }

    /**
     * Keeps `sync-index.json` close behind the run, so that an app that is killed rather than stopped - which gets no
     * chance to write anything on its way out - still loses no more than the last couple of seconds of transfers.
     * Throttled the same way as [scheduleLiveRescan], since the index is rewritten whole and a run finishes a file
     * several times a second. Whatever this skips, the write at the end of the run covers.
     */
    private fun scheduleIndexWrite(document: SyncIndexDocument) {
        if (indexWriteJob?.isActive == true) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastIndexWriteAt < INDEX_WRITE_INTERVAL_MS) return
        lastIndexWriteAt = now
        indexWriteJob = scope.launch { saveIndex(document) }
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
                // account is worthless rather than merely stale.
                val document = loadIndex()
                if (document.accountId != accountIdOf(account)) {
                    saveIndex(SyncIndexDocument())
                }
                _syncState.update {
                    SyncState.Connected(account = account, progress = null, lastSyncedAt = null, lastOutcome = null)
                }
                true
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

    /**
     * Who the account is, as far as the index is concerned. The display name is what every provider can offer, and
     * comparing it only has to answer "is this the same account as last time".
     */
    private fun accountIdOf(account: SyncAccount) = "${account.providerId.id}:${account.email ?: account.displayName}"

    private fun Throwable.toFailureReason() = when (this) {
        is SyncAuthorizationException -> SyncFailureReason.AUTHORIZATION
        is SyncNetworkException -> SyncFailureReason.NETWORK
        else -> SyncFailureReason.UNKNOWN
    }

    // Documents

    private suspend fun loadIndex() = try {
        syncStateLocalSource.loadSyncIndex()?.let { json.decodeFromString<SyncIndexDocument>(it) } ?: SyncIndexDocument()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the sync index: ${exception.message}")
        SyncIndexDocument()
    }

    private suspend fun saveIndex(document: SyncIndexDocument) = syncStateLocalSource.saveSyncIndex(json.encodeToString(document))

    private companion object {
        /** Often enough that the counters visibly move, rarely enough that the reading costs less than the syncing. */
        const val LIVE_RESCAN_INTERVAL_MS = 1000L

        /** How much of a run a killed app can lose at most, traded against rewriting the whole index per file. */
        const val INDEX_WRITE_INTERVAL_MS = 2000L

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
