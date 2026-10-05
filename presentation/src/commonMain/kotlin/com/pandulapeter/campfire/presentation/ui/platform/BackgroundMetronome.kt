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

/**
 * Keeps a playing click alive and in reach while the app is not the thing the user is looking at: the twin of
 * [SyncNotifier]. The click itself belongs to the app and plays on in the background by itself, the platform's audio
 * output being what it plays through; what a shell adds is what the platform needs to let it - a foreground service
 * with a media session and its notification on Android, Now Playing and the lock screen's controls on iOS, the browser's
 * media session on the web - and the controls that stop it from there, which reach the engine directly.
 *
 * Only ever told about a click being shown or changed, and that it is over once it has been shown: a shell follows the
 * engine's own state for stopping, since the composition may be gone (an Android activity finished with a song's click
 * still playing) by the time the click ends.
 */
fun interface MetronomeNotifier {

    fun onMetronomeNotificationChanged(notification: MetronomeNotification?)
}

/**
 * What a platform shows for a playing click, already translated, since the language chosen in the app is only visible
 * to the UI.
 *
 * @param title The song the click plays for, or the name of the metronome itself.
 * @param body The tempo and the time signature, as "96 BPM · 4/4".
 */
data class MetronomeNotification(
    val channelName: String,
    val title: String,
    val body: String,
    val stopLabel: String,
)

/** No-op by default, which is right for the desktop, whose window keeps the process and has no media controls. */
val LocalMetronomeNotifier = staticCompositionLocalOf { MetronomeNotifier { } }
