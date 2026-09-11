/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
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
 * [body] is what to show right now and is enough on its own for a platform that only ever shows what it is handed.
 * [preparingBody] and [progressBodyFormat] are the same sentence taken apart, for a platform whose notification
 * outlives the UI that resolved these strings: on Android the run and its notification carry on after the app is
 * swiped away, and from that point there is no composition left to send a new [body] as the count goes up. Handing
 * the service the pieces lets it keep the text moving on its own, still in the language chosen in the app.
 *
 * @param body What the run is doing right now, which changes as it goes.
 * @param preparingBody What to say before there is anything to count.
 * @param progressBodyFormat The sentence to put the two counts into, filled in with [withSyncCounts].
 * @param stopLabel The label of the action that stops it, where the platform can offer one.
 */
data class SyncNotification(
    val channelName: String,
    val title: String,
    val body: String,
    val preparingBody: String,
    val progressBodyFormat: String,
    val stopLabel: String,
    val progress: SyncProgress,
)

/**
 * Puts the two counts into the sentence that carries them.
 *
 * The placeholders are plain text rather than the localization plugin's `%1$d`, because this substitution has to
 * happen in two places the plugin cannot reach: it only formats inside a composable, and the Android sync service
 * renders the same sentence long after the composition that resolved it is gone (see `SyncNotification`). Keeping
 * the sentence itself in one place per language is worth more here than the plugin's formatting.
 */
fun String.withSyncCounts(completed: Int, total: Int) = replace(COMPLETED_PLACEHOLDER, completed.toString())
    .replace(TOTAL_PLACEHOLDER, total.toString())

private const val COMPLETED_PLACEHOLDER = "{completed}"
private const val TOTAL_PLACEHOLDER = "{total}"

/** No-op by default, which is exactly right for the desktop and the web. */
val LocalSyncNotifier = staticCompositionLocalOf { SyncNotifier { } }
