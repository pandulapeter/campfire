/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.pandulapeter.campfire.tuner.implementation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError

/**
 * The audio session the microphone and the tones share, active while either of them is in use and given back with
 * `notifyOthersOnDeactivation` once neither is. With the microphone allowed it is `playAndRecord` in `measurement`
 * mode, which turns the voice processing off, playing through the speaker rather than the earpiece and with no
 * Bluetooth option, so that a headset never becomes the input; without it, a tone alone is `playback`, since touching
 * a recording category with no permission is what makes the system ask on its own.
 *
 * Both users call in from the tuner's one confined coroutine, so the count needs no lock.
 */
@Single
internal class IosTunerSession {

    private var users = 0

    val isRecordingAllowed get() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    /** Takes the session for one more user; false where the system would not activate it. */
    fun acquire(): Boolean {
        val session = AVAudioSession.sharedInstance()
        val isActive = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val isSet = if (isRecordingAllowed) {
                session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionDefaultToSpeaker, error.ptr)
            } else {
                session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, error.ptr)
            }
            isSet && session.setActive(true, error.ptr)
        }
        if (isActive) users++
        return isActive
    }

    fun release() {
        if (users == 0) return
        users--
        if (users == 0) {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)
            }
        }
    }
}
