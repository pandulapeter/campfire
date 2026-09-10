package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.pandulapeter.campfire.data.model.domain.SyncProgress

/**
 * Keeps a sync run alive and visible while the app is not the thing the user is looking at.
 *
 * A run already belongs to the app rather than to a screen (see `SyncRepository.synchronize`), but on a phone that
 * is not enough by itself: Android stops a process nobody can see, and iOS suspends one. Each shell therefore has to
 * tell its platform that something worth keeping alive is going on, in the way that platform understands - a
 * foreground service on Android, a background task on iOS - and both show it to the user as a notification.
 *
 * A shell rather than the shared UI, for the same reason as [FilePicker]: a service and a notification belong to the
 * host application. Desktop and the web provide nothing, because neither needs anything.
 */
fun interface SyncNotifier {

    /** Called with what to show while a run is going, and with null the moment there is nothing to show. */
    fun onSyncNotificationChanged(notification: SyncNotification?)
}

/**
 * Everything a platform needs in order to show one run. The words arrive already translated, because the language
 * the user picked is only visible to the UI - a notification built from Android resources would follow the system's
 * language instead, and would be in the wrong one for anybody who changed it in the app.
 *
 * @param body What the run is doing right now, which changes as it goes.
 * @param stopLabel The label of the action that stops it, where the platform can offer one.
 */
data class SyncNotification(
    val channelName: String,
    val title: String,
    val body: String,
    val stopLabel: String,
    val progress: SyncProgress
)

/** No-op by default, which is exactly right for the desktop and the web. */
val LocalSyncNotifier = staticCompositionLocalOf { SyncNotifier { } }
