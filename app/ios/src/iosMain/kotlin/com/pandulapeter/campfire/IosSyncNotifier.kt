package com.pandulapeter.campfire

import com.pandulapeter.campfire.presentation.ui.platform.SyncNotification
import com.pandulapeter.campfire.presentation.ui.platform.SyncNotifier
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
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
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
        }
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
        ) { }
    }

    private fun removeNotification() = UNUserNotificationCenter.currentNotificationCenter()
        .removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_ID))

    private companion object {
        /** Fixed, so that each update replaces the previous notification instead of stacking another one up. */
        const val NOTIFICATION_ID = "sync"
    }
}
