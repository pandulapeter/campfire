/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.sync

import android.app.Activity
import androidx.core.content.ContextCompat
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification

/**
 * Starts and dismisses [CampfireSyncService] for [activity], which tells it when the app is being left and when it is
 * back in front.
 */
internal class SyncServiceStarter(private val activity: Activity) {

    /**
     * The run the composition last said is going, or null when none is. Kept rather than acted on while the app is in
     * front, see [onSyncNotificationChanged].
     */
    private var syncNotification: SyncNotification? = null

    /**
     * The words the service was last started with. The counts need no forwarding - the service renders them from the
     * sync state itself - so a run's progress starts it again only when it is not running (it stops itself when it
     * sees a run end, which the activity may not have been watching) or when the words changed (a language switch).
     */
    private var lastSyncServiceWords: List<String>? = null

    /** Between a pause that was the user leaving - not a rotation - and the next resume. */
    private var isLeaving = false

    /**
     * Whether the service has been asked to start since the app was last left. [CampfireSyncService.isRunning] only
     * turns true once the service has got the intent, and the same run may be handed over again before that.
     */
    private var hasStartedSyncServiceSinceLeaving = false

    /**
     * The service is only started once the app is being left: in front, the activity keeps the process alive anyway,
     * and a foreground service shows its notification - Stop button and all - at once, which for the run every edit
     * starts ten seconds later would be a notification after nearly every change the user makes. What arrives while the
     * app is in front is kept for [onLeaving]; what arrives after it starts the service there and then.
     */
    fun onSyncNotificationChanged(notification: SyncNotification?) {
        syncNotification = notification
        if (notification == null) {
            dismissSyncService()
        } else if (isLeaving) {
            startSyncService(notification)
        }
    }

    /**
     * Called from the activity's `onPause` where it is the user leaving rather than a rotation: the last moment the
     * service may be started.
     */
    fun onLeaving() {
        isLeaving = true
        syncNotification?.let(::startSyncService)
    }

    /** Called from the activity's `onResume`. */
    fun onReturned() {
        isLeaving = false
        hasStartedSyncServiceSinceLeaving = false
        dismissSyncService()
    }

    /**
     * Starts the service that keeps a sync run alive once the user has left the app. Building the notification is the
     * service's job; what arrives here is only what it should say.
     */
    private fun startSyncService(notification: SyncNotification) {
        val words = listOf(
            notification.channelName,
            notification.title,
            notification.preparingBody,
            notification.progressBodyFormat,
            notification.stopLabel,
        )
        if (words == lastSyncServiceWords && (CampfireSyncService.isRunning || hasStartedSyncServiceSinceLeaving)) return
        try {
            ContextCompat.startForegroundService(
                activity,
                CampfireSyncService.intent(
                    context = activity,
                    channelName = notification.channelName,
                    title = notification.title,
                    preparingBody = notification.preparingBody,
                    progressBodyFormat = notification.progressBodyFormat,
                    stopLabel = notification.stopLabel,
                    completed = notification.progress.completed,
                    total = notification.progress.total,
                ),
            )
            lastSyncServiceWords = words
            hasStartedSyncServiceSinceLeaving = true
        } catch (exception: Exception) {
            // A notification that cannot be shown must never take the sync down with it: the run carries on, it
            // just stops surviving the app being left.
            println("Could not start the sync service: ${exception.message}")
        }
    }

    /**
     * Takes the service down, if this activity or an earlier one in the process started it. Never stops the run:
     * see [CampfireSyncService.dismissIntent].
     */
    private fun dismissSyncService() {
        if (lastSyncServiceWords == null && !CampfireSyncService.isRunning) return
        lastSyncServiceWords = null
        try {
            activity.startService(CampfireSyncService.dismissIntent(activity))
        } catch (exception: Exception) {
            println("Could not stop the sync service: ${exception.message}")
        }
    }
}
