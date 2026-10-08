/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)

package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import com.pandulapeter.campfire.data.repository.implementation.base.recovering
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncEngine
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncIndexDocument
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncKey
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncedPreferencesSync
import com.pandulapeter.campfire.data.repository.implementation.sync.indexKey
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.api.SyncIndexLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.model.redirectParameters
import kotlinx.coroutines.cancelAndJoin
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.measureTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    private val syncIndexLocalSource: SyncIndexLocalSource,
    /**
     * Sync writes song and setlist files behind these two repositories' backs, so it is the one thing that has to
     * tell them to read the library again. It used to be the use case's job, back when a run finished before the
     * caller did; now that a run outlives whoever started it, this is the only place that reliably still exists
     * when it ends.
     */
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    setlistComparison: SetlistComparison,
    libraryChanges: LibraryChanges,
    private val environment: RepositoryEnvironment,
) : SyncRepository {

    private val providers = syncProviders.all
    private val engine = SyncEngine(libraryFileLocalSource, libraryFileLock, setlistComparison) { key ->
        userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path)
    }
    private val syncedPreferencesSync = SyncedPreferencesSync(userPreferencesRepository, libraryFileLocalSource)
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Disconnected)
    override val syncState = _syncState.asStateFlow()
    override val availableProviders = providers.map { it.id }

    /**
     * A run belongs to the app, not to whatever screen started it. This repository is a singleton, so a sync
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

    /** When the automatic run [scheduleSynchronization] asked for is to start, or null while none is waiting. */
    private val scheduledRunDueAt = MutableStateFlow<TimeMark?>(null)

    /** One run at a time: two of them over the same files would each undo half of what the other did. */
    private val mutex = Mutex()

    /**
     * Start up is asked for once per ViewModel, which on Android is once per activity rather than once per process,
     * and the second one may arrive while the first is still waiting for the service to say whose account this is.
     */
    private val restoreMutex = Mutex()

    private var liveRefreshJob: Job? = null

    /** The library files the running run has changed and the repositories have not read again yet. */
    private val changedFiles = mutableSetOf<SyncKey>()
    private val changedFilesMutex = Mutex()

    /** When the last live refresh ended and how long the next one has to wait for, see [scheduleLiveRefresh]. */
    @Volatile
    private var lastLiveRescanEnd: TimeMark? = null

    @Volatile
    private var liveRescanPause = LIVE_RESCAN_INTERVAL

    private var indexWriteJob: Job? = null
    private var accountRefreshJob: Job? = null

    /** The failed connection a [connect] started from, which backing out of it returns to, see [stateAfterBackingOut]. */
    private var stateBeforeConnecting: SyncState? = null
    private var lastIndexWrite: TimeMark? = null

    init {
        scope.launch { libraryChanges.changes.collect { scheduleSynchronization() } }
        scope.launch { syncedPreferencesSync.localChanges.collect { scheduleSynchronization() } }
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
        // A previous installation's credentials that the first launch could not forget are tried again before
        // anything reads them - and while they still cannot be forgotten, nothing is restored from them.
        val isForgettingOwed = recovering(
            describe = { "Could not tell whether the sync credentials are to be forgotten: ${it::class.simpleName}" },
            // Not knowing is not a reason to disconnect an ordinary installation, which is every one but this rare case.
            fallback = { false },
        ) { syncIndexLocalSource.isForgettingCredentialsOwed() }
        if (isForgettingOwed && !withContext(NonCancellable) { forgetStoredConnectionNow() }) {
            return disconnectedResult
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
        val connected = try {
            providers.firstOrNull { it.isConnected() }
        } catch (exception: LibraryStorageException) {
            // The credentials are there and could not be read right now. Shown as not connected, the user would
            // connect again and the new authorization would be written over tokens that still work; this says
            // what happened, starts no run, and the next start - or the next attempt - reads them again.
            println("Could not read the stored sync credentials: ${exception.message}")
            _syncState.update { SyncState.ConnectionFailed(providers.first().id, SyncFailureReason.STORAGE) }
            return disconnectedResult
        }
        if (connected == null) {
            _syncState.update { SyncState.Disconnected }
            return disconnectedResult
        }
        // What this device already knows about the account goes on screen before the service is asked anything: on a
        // bad network that answer can take over a minute, and until it came the settings screen would offer to connect
        // an account that is connected. Only a connection nothing was ever stored about has to wait for it.
        val storedAccount = connected.storedAccount()
        // Start up must never end in an exception because of a service: the library is what the app is for, and sync
        // is a thing it does on the side.
        val account = storedAccount ?: recovering(
            describe = { "Could not restore the ${connected.id} connection: ${it.message}" },
            fallback = { null },
        ) { connected.loadAccount() }
        if (account == null) {
            // The credentials are there but the service will not say who they belong to, which only happens once
            // they have been revoked. Nothing is deleted here: the user is told, and disconnecting is their call.
            _syncState.update { SyncState.ConnectionFailed(connected.id, SyncFailureReason.AUTHORIZATION) }
            return disconnectedResult
        }
        val document = loadIndexOrNull() ?: SyncIndexDocument()
        // An automatic run is the one leaving the app starts, and being swiped away right after an edit is how it
        // routinely ends. Reported and left unrepeated, it would keep that very edit off the cloud folder until the
        // user asked for a run - for the sake of a message about a run nobody asked for, which the launch run that
        // carries the same changes makes pointless anyway.
        val wasInterrupted = document.isRunInProgress && !document.isAutomaticRunInProgress
        _syncState.update {
            SyncState.Connected(
                account = account,
                progress = null,
                lastSyncedAt = document.lastSyncedAt.takeIf { at -> at > 0 },
                // A run that was still marked as going when the app started is one the app never came back from.
                lastOutcome = if (wasInterrupted) SyncOutcome.Interrupted else null,
            )
        }
        if (document.isRunInProgress) {
            saveIndexQuietly(document.markedAsFinished())
        }
        if (storedAccount != null) {
            refreshAccount(connected)
        }
        return SyncRepository.RestoreResult(
            isConnected = true,
            didReturnFromAuthorization = false,
            wasInterrupted = wasInterrupted,
        )
    }

    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean {
        val provider = providers.firstOrNull { it.id == providerId } ?: return false
        // Asked before the pending authorization is written, so that the answer is about the credentials the failure
        // left. A failure with none stored - a first connection that never got that far - is disconnected at the next
        // launch, so backing out of retrying it must say disconnected too rather than repeat the old failure.
        stateBeforeConnecting = _syncState.value.takeIf { it is SyncState.ConnectionFailed && hasStoredCredentials(provider) }
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
                            stateAfterBackingOut()
                        } else {
                            SyncState.ConnectionFailed(providerId, SyncFailureReason.UNKNOWN)
                        }
                    }
                    stateBeforeConnecting = null
                    false
                }
            }
        } catch (exception: CancellationException) {
            // The user gave up, which the UI offers while an authorization is waiting. The clean up still has to
            // happen, so it runs outside the cancellation before the exception carries on unswallowed.
            withContext(NonCancellable) { discardPendingAuthorization() }
            _syncState.update { stateAfterBackingOut() }
            stateBeforeConnecting = null
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
        _syncState.update { if (it is SyncState.Connecting) stateAfterBackingOut() else it }
        stateBeforeConnecting = null
    }

    /** Whether [provider] still holds credentials, which is what the next launch restores a connection from. */
    private suspend fun hasStoredCredentials(provider: SyncProvider) = try {
        provider.isConnected()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        false
    }

    /**
     * What backing out of an authorization returns to: a connection that had failed stays failed, since its credentials
     * are still stored and the next launch would otherwise restore it as connected; anything else is disconnected.
     */
    private fun stateAfterBackingOut() = stateBeforeConnecting ?: SyncState.Disconnected

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
        scheduledRunDueAt.value = null
        syncJob.exchange(null)?.cancelAndJoin()
        withContext(NonCancellable) {
            providers.forEach { provider ->
                // Asked inside the try, so that credentials that cannot be read right now still end in a disconnect.
                recovering(
                    describe = { "Could not disconnect from ${provider.id}: ${it.message}" },
                    fallback = {},
                ) { if (provider.isConnected()) provider.disconnect() }
            }
            // The index describes a remote folder this device is no longer looking at. Kept, and it would read that
            // folder's every file as a deletion the next time something connects. Under the run lock, because a run
            // that was stopped a moment ago is not in syncJob any more and may still be writing the index on its way
            // out; no new one can slip in, since the providers above no longer say they are connected.
            mutex.withLock {
                syncIndexLocalSource.saveSyncIndex(null)
                stateBeforeConnecting = null
                _syncState.update { SyncState.Disconnected }
            }
        }
    }

    override suspend fun forgetStoredConnection() {
        restoreMutex.withLock { withContext(NonCancellable) { forgetStoredConnectionNow() } }
    }

    /**
     * Forgets every provider's credentials, the unfinished authorization and the index, and answers whether the
     * credentials are gone. It is noted as owed before anything is attempted and crossed off only once all of it
     * worked: a failure leaves a previous installation's account in the store, and without the note the next start
     * up - no longer a first launch - would restore it, and every one after it would too. [restoreConnection] asks
     * about the note first. Callers hold [restoreMutex].
     */
    private suspend fun forgetStoredConnectionNow(): Boolean {
        quietly("note that the sync credentials are to be forgotten") { syncIndexLocalSource.setForgettingCredentialsOwed(true) }
        var haveCredentialsGone = true
        providers.forEach { provider ->
            recovering(
                describe = { "Could not forget the ${provider.id} credentials: ${it.message}" },
                fallback = { haveCredentialsGone = false },
            ) { provider.forgetStoredCredentials() }
        }
        // A build with no provider at all still has to lose an authorization written down by one that had one.
        discardPendingAuthorization()
        // There cannot be an index on a fresh installation, and one that is somehow there describes a folder
        // this installation has never looked at.
        quietly("clear the sync index") { syncIndexLocalSource.saveSyncIndex(null) }
        _syncState.update { SyncState.Disconnected }
        if (haveCredentialsGone) {
            quietly("note that the sync credentials are forgotten") { syncIndexLocalSource.setForgettingCredentialsOwed(false) }
        }
        return haveCredentialsGone
    }

    /** For the clean-ups whose failure is worth a line in the log and nothing more. */
    private inline fun quietly(action: String, block: () -> Unit) =
        recovering(describe = { "Could not $action: ${it::class.simpleName}" }, fallback = {}, block = block)

    /**
     * Starts a run and returns; what it is doing and how it ended arrive through [syncState]. Fire and forget
     * because the run outlives whoever asked for it - the screen that started it may be gone long before it
     * finishes, and on Android the activity may be too.
     *
     * The policy belongs to this one run rather than to the state: an answer to "delete them here too?" is an answer
     * about the files that were gone when it was asked, not a setting every later run should inherit.
     */
    override fun synchronize(deletionPolicy: SyncDeletionPolicy) = startRun(deletionPolicy).also { isStarted ->
        // The run starts after every change an automatic one is waiting for, so it carries them too.
        if (isStarted) scheduledRunDueAt.value = null
    }

    override fun scheduleSynchronization() {
        if (_syncState.value !is SyncState.Connected) return
        scheduledRunDueAt.value = environment.timeSource.markNow() + AUTOMATIC_RUN_DELAY
    }

    override fun startScheduledSynchronization(): SyncProgress? {
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
        return (_syncState.value as? SyncState.Connected)?.progress
    }

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    override fun cancelSynchronization() {
        scheduledRunDueAt.value = null
        syncJob.exchange(null)?.cancel()
    }

    /**
     * Starts a run unless one is going, and answers whether it did.
     *
     * @param isAutomatic Whether nobody asked for this run - the one [scheduleSynchronization] waits to start. It is
     *   written into the index's marker, see [SyncIndexDocument.isAutomaticRunInProgress].
     */
    private fun startRun(deletionPolicy: SyncDeletionPolicy, isAutomatic: Boolean = false): Boolean {
        val current = syncJob.load()
        if (current?.isActive == true) return false
        // Lazy, so that a run that lost the race below is dropped before it has done anything.
        val run = scope.launch(start = CoroutineStart.LAZY) { runSynchronization(deletionPolicy, isAutomatic) }
        if (!syncJob.compareAndSet(current, run)) {
            run.cancel()
            return false
        }
        // Shown before the run gets to the thread it runs on, so that the state says a run is going by the time this
        // returns: see startScheduledSynchronization for the caller that depends on it. The run puts the same value
        // there again once it holds the lock, and clears it on every way out of its body - and the handler clears it
        // for a run that is cancelled before its body clears anything, waiting for the lock or not started yet.
        updateConnected { it.copy(progress = SyncProgress(), lastOutcome = null) }
        run.invokeOnCompletion { cause ->
            if (cause == null) return@invokeOnCompletion
            val current = syncJob.load()
            if (current == null || current === run || !current.isActive) {
                updateConnected { if (it.progress == null) it else it.copy(progress = null) }
            }
        }
        run.start()
        return true
    }

    /**
     * The whole of a run, including the two index writes that open it: everything from the moment the progress is
     * put on screen has to be inside the try, because the only way off any other path is with the progress still
     * showing and no way left to stop or restart it. Stopping a run during those opening writes did exactly that.
     */
    private suspend fun runSynchronization(deletionPolicy: SyncDeletionPolicy, isAutomatic: Boolean) {
        // No check for the lock being taken: a run asked for while the previous one is still clearing up waits for
        // it rather than being dropped, which is what "stop, then start again" looks like from the settings screen.
        // Two runs at once are prevented by syncJob in startRun().
        mutex.withLock {
            val provider = try {
                providers.firstOrNull { it.isConnected() }
            } catch (exception: LibraryStorageException) {
                // Outside the run's own try below, so a failure here would reach the scope's handler with nothing on
                // screen. A successful read is cached, but a failed credentials write clears it for the next one.
                println("Could not read the stored sync credentials: ${exception.message}")
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.STORAGE)) }
                return@withLock
            } ?: run {
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
                // A launch starts a run beside the first scan of the library, and both read every file, so the screen's
                // read goes first. Waited for rather than started again: a repository that is not Loading has been read,
                // or has failed, which a second read here would only repeat.
                if (songRepository.songs.first() is DataState.Loading) songRepository.loadSongsIfNeeded()
                if (setlistRepository.setlists.first() is DataState.Loading) setlistRepository.loadSetlistsIfNeeded()
                // Taken over before the marker is written, so that an index filed under the key an earlier version used
                // is under the current one from the first write of this run, however the run ends.
                val document = loadIndex().adoptedBy(connected.account)
                latestIndex = { document.markedAsRunning(isAutomatic) }
                // Written before anything moves, so that a run the app never comes back from is still recognisable
                // as interrupted next time - iOS suspending the app mid sync looks exactly like being killed.
                saveIndex(document.markedAsRunning(isAutomatic))
                val result = engine.synchronize(
                    provider = provider,
                    document = document,
                    accountId = connected.account.indexKey(),
                    onProgress = { progress ->
                        updateConnected { it.copy(progress = progress) }
                        scheduleLiveRefresh()
                    },
                    onIndexChanged = { snapshot ->
                        val marked = { snapshot().markedAsRunning(isAutomatic) }
                        latestIndex = marked
                        hasFinishedOperations = true
                        scheduleIndexWrite(marked)
                    },
                    onLocalFileChanged = { key -> changedFilesMutex.withLock { changedFiles += key } },
                    deletionPolicy = deletionPolicy,
                )
                indexWriteJob?.join()
                when (result) {
                    is SyncEngine.Result.DeletionsNeedConfirmation -> {
                        // Asked before the deletions moved, but not necessarily before anything did: a second pass
                        // can find the folder emptied after the first one had already brought files in, and those
                        // are on disk whether the question is answered.
                        saveIndexQuietly(latestIndex().markedAsFinished())
                        refreshLibraryAfterRun()
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
                        saveIndex(
                            result.index.copy(
                                lastSyncedAt = syncedAt,
                                syncedPreferences = syncedPreferences ?: result.index.syncedPreferences,
                            ),
                        )
                        // Reads only what the run changed, which for most runs is nothing.
                        refreshLibraryAfterRun()
                        updateConnected {
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
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Interrupted) }
                throw exception
            } catch (exception: Exception) {
                println("The sync run failed: ${exception.message}")
                withContext(NonCancellable) { finishRunCutShort(latestIndex) }
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
                withContext(NonCancellable) {
                    finishRunCutShort(latestIndex)
                    // Thrown from anywhere, a write included, so what the engine reported is not necessarily all it
                    // changed: the whole library is read again.
                    if (hasFinishedOperations) rescanLibrary()
                }
                updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.Failure(SyncFailureReason.UNKNOWN)) }
            } finally {
                // The one thing that has to be true on every way out, including any added later: no progress means
                // the settings screen offers to start a run again instead of offering to stop one that is over.
                updateConnected { if (it.progress == null) it else it.copy(progress = null) }
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
        println("Could not sync ${SyncedPreferencesDocument.FILE_NAME}: ${exception.message}")
        null
    }

    /**
     * Keeps the library counts moving while a run is going.
     *
     * Sync writes files behind the two repositories' backs, so nothing reads them again until something says to: this
     * hands them the files the run has changed so far, which they read again one by one. Throttled by what the last one
     * cost, see [liveRescanPauseAfter], since a first sync changes files several times a second and every refresh
     * rebuilds the lists downstream. Whatever is still waiting when the run ends is refreshed then.
     */
    private fun scheduleLiveRefresh() {
        if (liveRefreshJob?.isActive == true) return
        if (lastLiveRescanEnd?.let { it.elapsedNow() < liveRescanPause } == true) return
        liveRefreshJob = scope.launch {
            val duration = environment.timeSource.measureTime { refreshChangedFiles() }
            liveRescanPause = liveRescanPauseAfter(duration)
            lastLiveRescanEnd = environment.timeSource.markNow()
        }
    }

    /**
     * Keeps `sync-index.json` close behind the run, so that an app that is killed rather than stopped - which gets no
     * chance to write anything on its way out - still loses no more than the last couple of seconds of transfers.
     * Throttled the same way as [scheduleLiveRefresh], since the index is rewritten whole and a run finishes a file
     * several times a second. Whatever this skips, the write at the end of the run covers.
     */
    private fun scheduleIndexWrite(snapshot: () -> SyncIndexDocument) {
        if (indexWriteJob?.isActive == true) return
        if (lastIndexWrite?.let { it.elapsedNow() < INDEX_WRITE_INTERVAL } == true) return
        lastIndexWrite = environment.timeSource.markNow()
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
            val account = recovering(
                describe = { "Could not refresh the ${provider.id} account: ${it.message}" },
                fallback = { return@launch },
            ) { provider.loadAccount() }
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
     * Hands the files changed since the last refresh to the two repositories. They are taken out of [changedFiles]
     * before they are read, so a file the run changes again meanwhile waits for the next refresh, and put back when
     * this one is stopped before it got to them.
     */
    private suspend fun refreshChangedFiles() {
        val keys = changedFilesMutex.withLock { changedFiles.toSet().also { changedFiles.clear() } }
        if (keys.isEmpty()) return
        try {
            songRepository.refresh(keys.namesOf(LibraryFileKind.SONG))
            setlistRepository.refresh(keys.namesOf(LibraryFileKind.SETLIST))
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { changedFilesMutex.withLock { changedFiles += keys } }
            throw exception
        }
    }

    private fun Set<SyncKey>.namesOf(kind: LibraryFileKind) = filter { it.kind == kind }.mapTo(mutableSetOf()) { it.name }

    /**
     * The refresh a run ends with. A live one still reading is stopped first, which puts back what it had not got to,
     * and everything still waiting is read here.
     */
    private suspend fun refreshLibraryAfterRun() {
        liveRefreshJob?.cancelAndJoin()
        refreshChangedFiles()
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
        latestIndex?.let { saveIndexQuietly(it().markedAsFinished()) }
        refreshLibraryAfterRun()
    }

    /**
     * Forgets the authorization that was started, for the ways out that have already decided how they end. Clearing
     * writes the credentials document, and a storage that refuses that write must not replace the ending with an
     * exception of its own: [connect] runs in a launched coroutine with nobody to throw to, and the state has to
     * leave [SyncState.Connecting] whatever the storage says. What stays behind is a verifier nothing asks for
     * again, and the next authorization writes over it.
     */
    private suspend fun discardPendingAuthorization() = recovering(
        // Only the kind of failure: the message of one that came from the credentials document may quote it.
        describe = { "Could not clear the pending authorization: ${it::class.simpleName}" },
        fallback = {},
    ) { pendingAuthorizationStore.clearPendingAuthorization() }

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
                // Connected on this installation, so whatever an earlier one left in the store has just been written
                // over, and a forgetting still owed for it must not take this connection down at the next start up.
                quietly("note that the sync credentials are this installation's") {
                    syncIndexLocalSource.setForgettingCredentialsOwed(false)
                }
                // The account decides which remote folder the index describes, so one written for a different
                // account is worthless rather than merely stale. One that cannot be read is left for the run to find:
                // replaced here, it could be the good index of this very account.
                val document = loadIndexOrNull()?.adoptedBy(account)
                if (document != null && document.accountId != account.indexKey()) {
                    saveIndex(SyncIndexDocument())
                }
                stateBeforeConnecting = null
                _syncState.update {
                    SyncState.Connected(account = account, progress = null, lastSyncedAt = null, lastOutcome = null)
                }
                true
            } catch (exception: CancellationException) {
                // Giving up during the token exchange is not the exchange failing: connect() answers it by going back
                // to Disconnected, which it can only do if this arrives there as a cancellation. The tokens may be
                // stored by now - a provider writes them before it asks whose they are - and left there, the next
                // launch would find itself connected to an account the screen said was not, and sync it.
                withContext(NonCancellable) { forgetCredentialsOf(provider) }
                throw exception
            } catch (exception: Exception) {
                withContext(NonCancellable) { forgetCredentialsOf(provider) }
                fail(
                    providerId = pending.providerId,
                    reason = exception.toFailureReason(),
                    message = "The authorization could not be completed: ${exception.message}",
                )
            }
        }
    }

    /**
     * Drops whatever [provider] stored for an authorization that did not end connected, without telling the service:
     * there is nothing to revoke on the user's behalf for a connection they never saw made. A failure is only logged,
     * since the outcome the caller is on its way to report is the one that matters.
     */
    private suspend fun forgetCredentialsOf(provider: SyncProvider) = recovering(
        describe = { "Could not forget the ${provider.id} credentials: ${it.message}" },
        fallback = {},
    ) { provider.forgetStoredCredentials() }

    private fun fail(providerId: SyncProviderId, reason: SyncFailureReason, message: String): Boolean {
        println(message)
        _syncState.update { SyncState.ConnectionFailed(providerId, reason) }
        return false
    }

    private fun Throwable.toFailureReason() = when (this) {
        is SyncRunEndingException -> reason
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
        val text = syncIndexLocalSource.loadSyncIndex() ?: return SyncIndexDocument()
        // Off the caller's thread, which for restore() is the main one: the index has an entry per library file.
        return withContext(environment.computation) {
            recovering(
                describe = { "Could not decode the sync index: ${it.message}" },
                fallback = { SyncIndexDocument() },
            ) { json.decodeFromString<SyncIndexDocument>(text) }
        }
    }

    /** For the callers that only show what the index says or check whose it is, and must not throw because of it. */
    private suspend fun loadIndexOrNull() = recovering(
        describe = { "Could not read the sync index: ${it.message}" },
        fallback = { null },
    ) { loadIndex() }

    private suspend fun saveIndex(document: SyncIndexDocument) =
        syncIndexLocalSource.saveSyncIndex(withContext(environment.computation) { json.encodeToString(document) })

    /**
     * For the writes nobody is waiting on the result of - the periodic one and the ones made on the way out of a run.
     * The index only ever saves work: whatever it fails to record looks unsynced to the next run, which is a slower
     * run and not a wrong one, so a failure here is not worth more than a line in the log. A run whose storage is
     * really gone still says so, through the opening write and the one that completes it.
     */
    private suspend fun saveIndexQuietly(document: SyncIndexDocument) = recovering(
        describe = { "Could not write the sync index: ${it.message}" },
        fallback = {},
    ) { saveIndex(document) }

    private companion object {
        /** How much of a run a killed app can lose at most, traded against rewriting the whole index per file. */
        val INDEX_WRITE_INTERVAL = 2.seconds

        /** How long the library has to stay unchanged before an automatic run starts, see [scheduleSynchronization]. */
        val AUTOMATIC_RUN_DELAY = 10.seconds

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
 * How long the counters wait after a live refresh that took [duration]. A refresh reads every file changed since the
 * last one, which on a first sync of a large library is a lot of them, and each one rebuilds the lists downstream:
 * waiting a multiple of what it cost caps the share of a run that goes into re-reading what it has already written,
 * while a small run keeps the interval that makes the numbers visibly move.
 */
internal fun liveRescanPauseAfter(duration: Duration) = maxOf(LIVE_RESCAN_INTERVAL, duration * LIVE_RESCAN_PAUSE_FACTOR)

/** Often enough that the counters visibly move, where the reading costs next to nothing. */
internal val LIVE_RESCAN_INTERVAL = 1.seconds

/** One part reading to five parts not: a live refresh never takes more than about a sixth of a run. */
private const val LIVE_RESCAN_PAUSE_FACTOR = 5
