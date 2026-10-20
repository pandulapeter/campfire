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
import platform.Foundation.NSLock

/**
 * The audio session the microphone and the tones share, active while either of them is in use and given back with
 * `notifyOthersOnDeactivation` once neither is. While the input uses it, it is `playAndRecord` in `measurement` mode,
 * which turns the voice processing off, playing through the speaker rather than the earpiece and with no Bluetooth
 * option, so that a headset never becomes the input; a tone joining the input keeps that category. A tone heard alone
 * takes `playback`, which the built-in speaker plays markedly louder than `measurement`, and which is never a recording
 * category without the permission, whose touching is what makes the system ask on its own. The input joining a tone
 * switches the category, which reconfigures the route and so ends that tone; the screens start listening before any
 * tone can be played, and the scale sheet never listens, so that is accepted rather than given a second path.
 *
 * The input takes and gives it back from the tuner's one confined coroutine, but the tones give it back from their feed
 * thread or a system notification once they have faded out, so the count is kept under a lock.
 */
@Single
internal class IosTunerSession {

    private val lock = NSLock()
    private var users = 0

    val isRecordingAllowed get() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    /** Takes the session for the input, only ever asked for once recording is allowed; false where it would not activate. */
    fun acquireForInput() = acquire(isInput = true)

    /** Takes the session for a tone, keeping the category of a session already held; false where it would not activate. */
    fun acquireForTone() = acquire(isInput = false)

    private fun acquire(isInput: Boolean): Boolean = locked {
        val session = AVAudioSession.sharedInstance()
        val isActive = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val isSet = when {
                isInput -> session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionDefaultToSpeaker, error.ptr)
                users == 0 -> session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, error.ptr)
                else -> true
            }
            isSet && session.setActive(true, error.ptr)
        }
        if (isActive) users++
        isActive
    }

    fun release() = locked {
        if (users == 0) return@locked
        users--
        if (users == 0) {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)
            }
        }
    }

    private inline fun <T> locked(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
