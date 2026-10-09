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

import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.darwin.NSObjectProtocol

/**
 * `AVAudioEngine`'s input node with a tap of 4096 frames, whose block runs on a queue of the engine's rather than on the
 * real-time thread, so Kotlin may run in it; it copies the frames into a [SampleRing]. Started only when the record
 * permission is granted, since touching the input otherwise makes the system ask on its own. An interruption (a call)
 * stops it as busy, a new route as disconnected (which the engine opens again once), the media services being reset as
 * failed.
 */
@Single
internal class IosAudioInput(private val session: IosTunerSession) : AudioInput {

    private val ring = SampleRing(SampleRing.CAPACITY)
    private var engine: AVAudioEngine? = null
    private var observers = emptyList<NSObjectProtocol>()

    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        stop()
        if (!session.isRecordingAllowed) return AudioInputStart.Refused(TunerStopReason.PERMISSION_DENIED)
        if (!session.acquire()) return AudioInputStart.Refused(TunerStopReason.MICROPHONE_BUSY)
        val engine = AVAudioEngine()
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        val sampleRate = format.sampleRate.toInt()
        if (sampleRate <= 0 || format.channelCount.toInt() == 0) {
            session.release()
            return AudioInputStart.Refused(TunerStopReason.NO_MICROPHONE)
        }
        ring.clear()
        val copy = FloatArray(TAP_FRAMES * 2)
        input.installTapOnBus(0u, TAP_FRAMES.toUInt(), format) { buffer, _ ->
            val channel = buffer?.floatChannelData?.get(0) ?: return@installTapOnBus
            val frames = buffer.frameLength.toInt().coerceAtMost(copy.size)
            for (index in 0 until frames) copy[index] = channel[index]
            ring.write(copy, frames)
        }
        engine.prepare()
        val isStarted = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(error.ptr)
        }
        if (!isStarted) {
            input.removeTapOnBus(0u)
            session.release()
            return AudioInputStart.Refused(TunerStopReason.FAILED)
        }
        this.engine = engine
        observers = observe(engine, listener)
        return AudioInputStart.Started(sampleRate)
    }

    override fun latest(window: FloatArray) = ring.latest(window)

    override fun stop() {
        val engine = engine ?: return
        this.engine = null
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers = emptyList()
        engine.inputNode.removeTapOnBus(0u)
        engine.stop()
        session.release()
    }

    private fun observe(engine: AVAudioEngine, listener: AudioInputListener): List<NSObjectProtocol> {
        val center = NSNotificationCenter.defaultCenter
        return listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) listener.onLost(TunerStopReason.MICROPHONE_BUSY)
            },
            // A new route (headphones plugged in or pulled) stops the engine on its own, with the input's format
            // possibly changed under it.
            center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) {
                listener.onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
            },
            center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) {
                listener.onLost(TunerStopReason.FAILED)
            },
        )
    }

    private companion object {
        const val TAP_FRAMES = 4_096
    }
}
