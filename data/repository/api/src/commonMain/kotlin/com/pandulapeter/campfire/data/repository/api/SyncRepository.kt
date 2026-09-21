/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import kotlinx.coroutines.flow.Flow

/**
 * Keeping the library in step with a cloud service the user connected.
 *
 * Unlike the other repositories this one has no cached list to hand out: what it holds is a state machine, and the
 * library itself keeps living in the song and setlist repositories. A run that changed files on disk therefore has
 * to be followed by a rescan of those, which is the job of `SynchronizeLibraryUseCase`.
 */
interface SyncRepository {

    val syncState: Flow<SyncState>

    /** The services this build can connect to. Empty when the build was not given the credentials for any. */
    val availableProviders: List<SyncProviderId>

    /**
     * Reads the stored credentials and finishes an authorization that was started before the app was last closed,
     * which is the ordinary case on the web: consent happens on another page, and the app starts again afterwards.
     *
     * May be called more than once in a process - on Android every new activity's ViewModel does. Only a call that
     * finds no connection in [syncState] reads anything; a later one answers from the state and never touches a run
     * that is going.
     */
    suspend fun restore(): RestoreResult

    /**
     * What start up found.
     *
     * @param isConnected Whether the app ended up connected, which is what tells the caller a first run is worth
     *   starting.
     * @param didReturnFromAuthorization Whether this start up was the answer to a consent page the app had been
     *   sent away to. True on the web, where the app stops existing while the user is on that page, and on Android
     *   when the process was killed behind the browser - it is what lets the UI put them back where they pressed the
     *   button. True whether the service said yes or no:
     *   an authorization that failed is exactly the case where the user most needs to see the screen that says so,
     *   so this reports that the app came back, not that it came back connected.
     * @param wasInterrupted Whether the last run never finished - the app was killed, swiped away or suspended while
     *   it was going. The caller must not start a run on its own then: the run would replace the message saying so
     *   before anyone could read it, and starting it again is one button away from that message.
     */
    data class RestoreResult(
        val isConnected: Boolean,
        val didReturnFromAuthorization: Boolean,
        val wasInterrupted: Boolean,
    )

    /**
     * Opens the service's consent page and connects if the user agrees. Returns whether it is now connected.
     *
     * @param completionPage What the page the browser lands on after consent should say. It is the one piece of
     *   Campfire's own text that lives outside the app, so it is handed down from the UI rather than written in the
     *   data layer, which has no access to the translations or to the language the user picked.
     */
    suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean

    /**
     * Gives up on an authorization that is waiting: forgets what was written down for it and leaves
     * [SyncState.Connecting]. Does nothing in any other state.
     *
     * Cancelling the caller's own [connect] does the same on its way out, and is still how the platform's half of
     * the wait is ended - the sheet on iOS, the desktop's socket. This is for the [connect] that is no longer there
     * to be cancelled: on the web it returns as soon as the page starts to navigate away, and a page the browser
     * then hands back as it was left (Back out of the consent page, a navigation that was stopped) is still
     * connecting with nothing going on behind it. A caller that does have a [connect] running cancels it and waits
     * for it first.
     */
    suspend fun cancelConnection()

    suspend fun disconnect()

    /**
     * Starts a run, if nothing is connected there is nothing to start, and if one is already going this does
     * nothing - so pressing the button twice cannot start two runs over the same files.
     *
     * Deliberately not suspend and returning nothing: a run outlives whoever asked for it, and what it is doing and
     * how it ended arrive through [syncState]. That is also what lets it carry on while the app is in the
     * background on Android, where the screen that started it may be gone.
     *
     * @param deletionPolicy What happens to files found gone from the remote folder. [SyncDeletionPolicy.ASK] stops a
     *   run that would delete most of the library and reports [SyncOutcome.DeletionsNeedConfirmation]; the other two
     *   are the answers to that question.
     */
    fun synchronize(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK)

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    fun cancelSynchronization()
}
