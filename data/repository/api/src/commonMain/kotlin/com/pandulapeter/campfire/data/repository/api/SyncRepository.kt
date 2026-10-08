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
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import kotlinx.coroutines.flow.Flow

/**
 * Keeping the library in step with a cloud service the user connected.
 *
 * Unlike the other repositories this one has no cached list to hand out: what it holds is a state machine, and the
 * library itself keeps living in the song and setlist repositories. A run writes files behind their backs, so this
 * repository hands them the files it changed (`SongRepository.refresh`, `SetlistRepository.refresh`) while it runs and
 * whichever way it ends.
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
     *   before anyone could read it, and starting it again is one button away from that message. False for an
     *   automatic run (see [scheduleSynchronization]) that was cut short: nobody asked for that one, and the launch run
     *   is what carries the changes it was carrying.
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
     * Forgets the credentials, the unfinished authorization and the index this device has stored, and makes no
     * request while doing it. For a fresh installation that finds a previous one's credentials still in the
     * platform's store - the iOS Keychain outlives an uninstall - where connecting is not something the user of
     * this installation has done. [disconnect] is the other one: that is the user disconnecting, and it tells the
     * service so.
     */
    suspend fun forgetStoredConnection()

    /**
     * Starts a run, if nothing is connected there is nothing to start, and if one is already going this does
     * nothing - so pressing the button twice cannot start two runs over the same files. Returns whether it started
     * one: false where a run is already going, which the request then does not survive, its [deletionPolicy]
     * included.
     *
     * Deliberately not suspend: a run outlives whoever asked for it, and what it is doing and how it ended arrive
     * through [syncState]. That is also what lets it carry on while the app is in the background on Android, where
     * the screen that started it may be gone.
     *
     * @param deletionPolicy What happens to files found gone from one side. [SyncDeletionPolicy.ASK] stops a run that
     *   would delete most of the library on this device or in the cloud folder and reports
     *   [SyncOutcome.DeletionsNeedConfirmation]; the other four are the answers to that question, two per direction.
     */
    fun synchronize(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK): Boolean

    /**
     * Asks for a run that nobody pressed a button for: the one every change the app makes to a song or setlist file asks
     * for on its own. It starts ten seconds after the last such request rather than after the first, so that a burst of
     * edits - a few tags toggled, an import of a hundred files - is carried by one run. A request made while a run is
     * going is honoured after that run, which may have read the library before the change. Ignored while nothing is
     * connected.
     *
     * Only these runs wait; the one a launch starts goes through [synchronize], like the button's. A run [synchronize]
     * starts makes the one waiting unnecessary and takes its place, and [cancelSynchronization] drops it along with the
     * run it stops, since a run the user has just stopped must not start again on its own a few seconds later.
     */
    fun scheduleSynchronization()

    /**
     * Starts the run [scheduleSynchronization] is waiting to start without waiting any longer, and does nothing when
     * none is. For the moment the app stops being the one in front: a phone keeps a run alive in the background only
     * once the app has told the platform about it, which it can only do while it is still in front, and on iOS a
     * suspended app would not get to start it at all.
     *
     * The run is started before this returns, and so is [syncState]'s progress, which is also what it returns: the
     * progress of the run that is going once it has returned, whether it started now or was already going, and null
     * when none is. A caller on its way out of the front has no frame left to wait for the state to arrive in - the
     * platform has to be told there and then. A run asked for while another one is going still follows that one.
     */
    fun startScheduledSynchronization(): SyncProgress?

    /** Stops a run where it is. What has already moved stays moved, and the next run picks up from there. */
    fun cancelSynchronization()
}
