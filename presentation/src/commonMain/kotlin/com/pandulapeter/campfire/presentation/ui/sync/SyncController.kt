/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.domain.api.sync.CancelSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.sync.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.sync.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.sync.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.sync.ForgetSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.sync.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.sync.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.sync.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeOnReturnUseCase
import com.pandulapeter.campfire.domain.api.sync.WatchRemoteChangesUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The connection to a sync provider and the runs it does, see the root `CLAUDE.md`'s Sync section. */
internal class SyncController(
    private val scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val getSyncState: GetSyncStateUseCase,
    getSyncProviders: GetSyncProvidersUseCase,
    private val connectSyncProvider: ConnectSyncProviderUseCase,
    private val disconnectSyncProvider: DisconnectSyncProviderUseCase,
    private val cancelSyncConnection: CancelSyncConnectionUseCase,
    private val forgetSyncConnection: ForgetSyncConnectionUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val synchronizeOnReturn: SynchronizeOnReturnUseCase,
    private val watchRemoteChanges: WatchRemoteChangesUseCase,
    private val cancelSynchronization: CancelSynchronizationUseCase,
) {

    /**
     * Read straight from its own repository, like the preferences and for the same reason: sync runs on its own
     * schedule, and a settings screen must not wait for a scan of the library to say whether an account is on.
     */
    val syncState = getSyncState().asState(scope, SyncState.Disconnected)

    /** True while a sync run is going. Acted on rather than drawn: a restart the app offers by itself waits for it. */
    val isSyncing = syncState.map { it is SyncState.Connected && it.isSyncing }.asState(scope, false)

    /** Fixed for the life of the build, so it is a value rather than a flow. Empty means sync is not configured. */
    val syncProviders: List<SyncProviderId> = getSyncProviders()

    /**
     * The provider a Connect button connects, null where sync is not configured: a build offers one, the only entry of
     * [syncProviders]. A connection made again for an account goes to the account's own provider instead.
     */
    val offeredSyncProvider = syncProviders.firstOrNull()

    /**
     * Kept so that it can be cancelled: an authorization waits on a browser that may never come back, and the
     * cancellation is what closes the sheet on iOS and releases the desktop's socket. While an attempt is being
     * given up on, this is the job doing that, so that the next attempt waits for it.
     */
    private var syncConnectionJob: Job? = null

    /** Whether the user gave up on the latest attempt, which a failed reconnect is told apart from. */
    private var isConnectionCancelled = false

    /**
     * @param completionPage The words the desktop's redirect page shows, resolved by the screen because that is
     *   where the translations and the language the user picked are, see `AuthorizationCompletionPage`.
     */
    fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) {
        if (syncConnectionJob?.isActive == true) return
        isConnectionCancelled = false
        // Connecting writes the credentials, and a storage that refuses them is reported by the repository as a
        // failed connection. This is for the exception that one day is not: it must cost a message, not the app.
        syncConnectionJob = messageSink.launchLibraryChange("connect sync") {
            val stateBefore = getSyncState().first()
            val isConnected = connectSyncProvider.invoke(providerId, completionPage)
            // The answer is false for a Cancel too, and a reconnect that failed must not look like one: the screen
            // would simply show the account again, as if nothing had been tried.
            if (isFailedReconnect(stateBefore, isConnected, getSyncState().first(), isConnectionCancelled)) {
                messageSink.sendMessage(Message.ReconnectFailed)
            }
        }
    }

    /**
     * Gives up on an authorization that is waiting, which is the way out of a browser the user closed. Cancelling
     * the job is what ends the platform's half of the wait; the repository is asked as well because on the web the
     * job is over as soon as the page starts to navigate away, and a page the browser hands back as it was left is
     * still connecting with nothing to cancel.
     */
    fun cancelSyncConnection() {
        val connection = syncConnectionJob
        isConnectionCancelled = true
        syncConnectionJob = scope.launch {
            // Joined first, so that the clean up of an attempt that is being given up on cannot land on the next
            // one: until it is over this job is the active one, and connectSyncProvider() refuses to start another.
            connection?.cancelAndJoin()
            cancelSyncConnection.invoke()
        }
    }

    /** A disconnect that is still going, which a second confirmation leaves to finish rather than starting another. */
    private var disconnectJob: Job? = null

    fun disconnectSyncProvider() {
        if (disconnectJob?.isActive == true) return
        disconnectJob = messageSink.launchLibraryChange("disconnect sync") { disconnectSyncProvider.invoke() }
    }

    /**
     * Not launched in [scope]: a run belongs to the app rather than to this screen, and carries on while
     * the user moves around it or leaves it entirely. The repository refuses a second run while one is going, so a
     * second tap costs nothing.
     */
    fun synchronizeLibrary(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) = synchronizeLibrary.invoke(deletionPolicy)

    fun cancelSynchronization() = cancelSynchronization.invoke()

    /** The app came back to the front, where the cloud folder may have moved on while it was away. */
    fun onAppReturned() = synchronizeOnReturn.invoke()

    /** Brings other devices' changes over while the caller runs, which is while the app is in front. */
    suspend fun watchRemoteChanges() = watchRemoteChanges.invoke()

    /**
     * Picks a connected account back up, finishes a consent the app was closed in the middle of, and runs a first
     * sync. Its own coroutine, so that a slow network never holds up the library appearing on screen.
     *
     * @param isFirstLaunch Whether this is the first launch of this installation, see `CampfireViewModel.isFirstLaunch`.
     * @param onConsentAnswered Shows the answer to a consent page the app was sent away to.
     */
    fun startRestoring(isFirstLaunch: Deferred<Boolean>, onConsentAnswered: () -> Unit) = scope.launch {
        // On the web the consent page replaces the app, so this start up is the second half of a tap on Connect:
        // whether it ends up connected or not, the screen the tap was made on is the one the answer is on, which
        // onConsentAnswered puts back, since the page load forgot it. It does so before the token exchange, while
        // nobody has had the time to build on the start stack, and that screen shows the exchange going on.
        try {
            // A reinstall is the one case where credentials outlive the library: iOS leaves the Keychain item
            // behind while everything else goes, so a fresh installation would find itself connected to an
            // account nobody connected here, and its first run would upload the demo library into the user's
            // folder. In this coroutine rather than beside it, because it is the one thing that must happen
            // before restore() reads those credentials.
            if (isFirstLaunch.await()) forgetSyncConnection()
            restoreSync(onReturnedFromAuthorization = onConsentAnswered)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Sync is something the app does on the side: nothing about it may keep the library from appearing.
            println("Could not restore the sync connection: ${exception.message}")
        }
    }
}

/**
 * Whether a connection that answered not connected was a reconnect of a working connection that did not go through:
 * it started from [SyncState.Connected], is back there, and the user did not cancel it. A closed consent page reads the
 * same, and it too means the reconnect did not go through.
 */
internal fun isFailedReconnect(stateBefore: SyncState, isConnected: Boolean, stateAfter: SyncState, isCancelled: Boolean) =
    !isConnected && !isCancelled && stateBefore is SyncState.Connected && stateAfter is SyncState.Connected
