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

import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Keeps a sync run going while the app is not in front of the user, and shows what it is doing.
 *
 * iOS is stricter than Android here, and honestly so: a background task buys a few tens of seconds, not minutes.
 * A library that cannot be finished in that time is suspended mid run - which is exactly the case the index's
 * "a run was going" marker exists for, so the next start reports it as interrupted and carries on from where it
 * stopped rather than from the beginning.
 *
 * The notification is informational: iOS has no progress bar in a notification and no way to put a button on one
 * without a registered category, so tapping it opens the app, where the settings screen has the stop action.
 */
class IosSyncNotifier : SyncNotifier {

    private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var hasRequestedAuthorization = false

    /**
     * Takes down whatever the last launch left behind.
     *
     * A run cannot survive the process, but its notification can: iOS suspending the app mid run is the expected
     * end of a large library (it is what the index's "a run was going" marker exists for), and the app is then
     * never asked to take the notification down, because the shared effect only says "nothing to show" after it
     * has shown something and a fresh launch has shown nothing yet. Without this, Notification Center keeps
     * claiming a sync is in progress for as long as the user leaves it there. Android reaches the same end by a
     * different road and clears it the same way, from the application - see CampfireSyncService.
     */
    init {
        removeNotification()
    }

    override fun onSyncNotificationChanged(notification: SyncNotification?) {
        if (notification == null) {
            removeNotification()
            endBackgroundTask()
        } else {
            beginBackgroundTask()
            showNotification(notification)
        }
    }

    /**
     * Asks iOS for time to finish once the app is backgrounded. The expiration handler is not optional: a task that
     * is not ended when iOS asks gets the app killed, and a killed app is a worse outcome than a stopped sync.
     */
    private fun beginBackgroundTask() {
        if (backgroundTask != UIBackgroundTaskInvalid) return
        backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("sync") {
            endBackgroundTask()
        }
    }

    private fun endBackgroundTask() {
        if (backgroundTask == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(backgroundTask)
        backgroundTask = UIBackgroundTaskInvalid
    }

    /**
     * Re-posted under the same identifier on every change, which is how a notification is updated on iOS: the new
     * request replaces the old one rather than adding a second.
     */
    private fun showNotification(notification: SyncNotification) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        if (!hasRequestedAuthorization) {
            hasRequestedAuthorization = true
            // Asking is asynchronous, and anything posted while the answer is still outstanding is dropped rather
            // than held until it arrives - so the first notification of the first run has to be posted from the
            // answer itself. Every later one finds the question already settled and goes straight out below.
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert) { isGranted, _ ->
                if (isGranted) {
                    center.post(notification)
                }
            }
        } else {
            center.post(notification)
        }
    }

    private fun UNUserNotificationCenter.post(notification: SyncNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            // The counterpart of the Android channel's silence and low importance: a passive notification is added
            // to the list without lighting the screen up. A sync is something to be able to look at, not something
            // to be interrupted by - which is also why no sound is asked for or set.
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive)
        }
        addNotificationRequest(
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
    }
}
