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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * `AVAudioSession.recordPermission`, read again on every resume, and `requestRecordPermission`: the deployment target
 * is below iOS 17, so this is the session's API rather than `AVAudioApplication`'s. The settings are the app's own page
 * of the Settings app.
 */
@Composable
internal actual fun rememberMicrophonePermission(): MicrophonePermission {
    var status by remember { mutableStateOf(currentStatus()) }
    LifecycleResumeEffect(Unit) {
        status = currentStatus()
        onPauseOrDispose { }
    }
    return MicrophonePermission(
        status = status,
        canAskAgain = status == MicrophoneStatus.NOT_ASKED,
        request = {
            AVAudioSession.sharedInstance().requestRecordPermission { _ ->
                // Answered on a queue of the system's; the state is the composition's.
                dispatch_async(dispatch_get_main_queue()) { status = currentStatus() }
            }
        },
        openSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
        },
    )
}

private fun currentStatus() = when (AVAudioSession.sharedInstance().recordPermission) {
    AVAudioSessionRecordPermissionGranted -> MicrophoneStatus.GRANTED
    AVAudioSessionRecordPermissionDenied -> MicrophoneStatus.DENIED
    else -> MicrophoneStatus.NOT_ASKED
}
