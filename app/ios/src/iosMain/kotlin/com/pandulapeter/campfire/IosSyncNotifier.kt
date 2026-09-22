/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps a sync run going while the app is not in front of the user, and shows what it is doing.
 *
 * iOS is stricter than Android here, and honestly so: a background task buys a few tens of seconds, not minutes.
 * A library that cannot be finished in that time is stopped when iOS says the time is up, the way Android's
 * `onTimeout` stops it, so it writes its index and reports itself as interrupted; a process killed before that is
 * still found by the index's "a run was going" marker at the next start. Either way the next Sync now carries on from
 * where it stopped rather than from the beginning.
 *
 * It follows the run itself rather than the composition. Compose stops collecting and stops drawing the moment the
 * scene leaves the foreground, so a notifier driven by the UI hears nothing more once the app is out of sight - which
 * is exactly when the notification is for. The composition only hands over the words, already in the language chosen
 * in the app; the counts come from the sync state, as they do in the Android service.
 *
 * It posts only while the app is in the background. A notification that arrives for an app in the foreground is
 * silenced by iOS unless a notification center delegate asks for it to be shown, and there the settings screen shows
 * the run anyway, so the notification is posted when the app leaves and taken down when it comes back.
 *
 * The notification is informational: iOS has no progress bar in a notification and no way to put a button on one
 * without a registered category, so tapping it opens the app, where the settings screen has the stop action.
 */
class IosSyncNotifier(
    syncState: Flow<SyncState>,
    /** Stops the run when iOS ends the background time, see [beginBackgroundTask]. */
    private val onBackgroundTimeExpired: () -> Unit,
) : SyncNotifier {

    private val scope = MainScope()
    private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var hasRequestedAuthorization = false
    private var words: SyncNotification? = null
    private var progress: SyncProgress? = null
    private var isInBackground = false
    private var notificationUpdateJob: Job? = null

    /**
     * Takes down whatever the last launch left behind.
     *
     * A run cannot survive the process, but its notification can: iOS suspending the app mid run is the expected
     * end of a large library (it is what the index's "a run was going" marker exists for), and nothing is left to
     * take the notification down. Without this, Notification Center keeps claiming a sync is in progress for as long
     * as the user leaves it there. Android reaches the same end by a different road and clears it the same way, from
     * the application - see CampfireSyncService.
     *
     * The observers are never removed, since there is one notifier for the life of the process.
     */
    init {
        removeNotification()
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            isInBackground = true
            updateNotification()
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            isInBackground = false
            cancelNotificationUpdate()
            removeNotification()
        }
        scope.launch {
            syncState
                .map { (it as? SyncState.Connected)?.progress }
                .distinctUntilChanged()
                .collect(::onProgressChanged)
        }
    }

    /**
     * Only the words are taken from here, see the note on the class. Asking for permission happens here as well: a run
     * is started from the settings screen, so this is the moment the user is looking at the app and the question means
     * something, and by the time the app leaves and something is posted, the answer is in.
     */
    override fun onSyncNotificationChanged(notification: SyncNotification?) {
        if (notification == null) return
        words = notification
        if (!hasRequestedAuthorization) {
            hasRequestedAuthorization = true
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(UNAuthorizationOptionAlert) { _, _ -> }
        }
    }

    private fun onProgressChanged(newProgress: SyncProgress?) {
        val wasPreparing = progress?.isPreparing != false
        progress = newProgress
        if (newProgress == null) {
            cancelNotificationUpdate()
            removeNotification()
            endBackgroundTask()
        } else {
            // From the state rather than from a frame, so the time is asked for even when the app leaves before the run
            // has been drawn once.
            beginBackgroundTask()
            if (isInBackground) {
                scheduleNotificationUpdate(isCountingStarted = wasPreparing && !newProgress.isPreparing)
            }
        }
    }

    /**
     * Asks iOS for time to finish once the app is backgrounded. The expiration handler is not optional: a task that
     * is not ended when iOS asks gets the app killed, and a killed app is a worse outcome than a stopped sync. It stops
     * the run before ending the task, and does not wait for the run to wind down, since iOS also kills an app whose
     * handler does not return promptly: the clean up resumes with the app if it is suspended half way.
     */
    private fun beginBackgroundTask() {
        if (backgroundTask != UIBackgroundTaskInvalid) return
        backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("sync") {
            // Stopped rather than left to be frozen mid request: frozen, it resumes into requests that have timed out and
            // reports a network failure; stopped, it writes its index and says it was interrupted, which is what it was.
            onBackgroundTimeExpired()
            endBackgroundTask()
        }
    }

    private fun endBackgroundTask() {
        if (backgroundTask == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(backgroundTask)
        backgroundTask = UIBackgroundTaskInvalid
    }

    /**
     * Posts the latest counts at most once per [NOTIFICATION_UPDATE_INTERVAL], from one delayed job that reads them
     * when it fires: a run finishes several files a second, and a job that only skipped updates inside the interval
     * would leave the count where the last burst stopped. The step from preparing to counting goes out at once, as it
     * changes what the notification says rather than one of its numbers.
     */
    private fun scheduleNotificationUpdate(isCountingStarted: Boolean) {
        if (isCountingStarted) {
            cancelNotificationUpdate()
            updateNotification()
        } else if (notificationUpdateJob?.isActive != true) {
            notificationUpdateJob = scope.launch {
                delay(NOTIFICATION_UPDATE_INTERVAL)
                updateNotification()
            }
        }
    }

    private fun cancelNotificationUpdate() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }

    /**
     * Re-posted under the same identifier on every change, which is how a notification is updated on iOS: the new
     * request replaces the old one rather than adding a second.
     */
    private fun updateNotification() {
        val words = words ?: return
        val progress = progress ?: return
        if (!isInBackground) return
        val content = UNMutableNotificationContent().apply {
            setTitle(words.title)
            setBody(
                if (progress.isPreparing) {
                    words.preparingBody
                } else {
                    words.progressBodyFormat.withSyncCounts(progress.completed, progress.total)
                }
            )
            // The counterpart of the Android channel's silence and low importance: a passive notification is added
            // to the list without lighting the screen up. A sync is something to be able to look at, not something
            // to be interrupted by - which is also why no sound is asked for or set.
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive)
        }
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
        ) { }
    }

    /** Both lists: a request that has not been delivered yet is not a delivered notification, and outlives one. */
    private fun removeNotification() = UNUserNotificationCenter.currentNotificationCenter().let { center ->
        center.removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_ID))
        center.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_ID))
    }

    private companion object {
        /** Fixed, so that each update replaces the previous notification instead of stacking another one up. */
        const val NOTIFICATION_ID = "sync"

        val NOTIFICATION_UPDATE_INTERVAL = 1.seconds
    }
}
