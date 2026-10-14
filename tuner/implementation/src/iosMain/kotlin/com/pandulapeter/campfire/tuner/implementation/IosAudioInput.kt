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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * `AVAudioEngine`'s input node with a tap of 4096 frames, whose block runs on a queue of the engine's rather than on the
 * real-time thread, so Kotlin may run in it; it copies the frames into a [SampleRing]. Started only when the record
 * permission is granted, since touching the input otherwise makes the system ask on its own. An interruption (a call)
 * stops it as busy, a new route as disconnected (which the engine opens again once), the media services being reset as
 * failed. A tap that stops arriving for [STALL_REOPEN] is reported as disconnected too: another user of the shared
 * session (the metronome's stop) deactivating it stops the input with no notice at all.
 */
@Single
internal class IosAudioInput(private val session: IosTunerSession) : AudioInput {

    private val ring = SampleRing(SampleRing.CAPACITY)
    private var engine: AVAudioEngine? = null
    private var observers = emptyList<NSObjectProtocol>()
    private var listener: AudioInputListener? = null
    private var lastPosition = AudioInput.NO_WINDOW
    private var lastMoved = TimeSource.Monotonic.markNow()
    private var isLossReported = false

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
        this.listener = listener
        lastPosition = AudioInput.NO_WINDOW
        lastMoved = TimeSource.Monotonic.markNow()
        isLossReported = false
        observers = observe(engine, listener)
        return AudioInputStart.Started(sampleRate)
    }

    /**
     * Also notices a tap that stopped arriving, measured from [start] so that one that never delivered is caught too. It
     * waits far longer than an iOS tap is ever late and than a call's or a new route's own notice takes to arrive, so
     * those still win with their own reason. Runs on the tuner's one coroutine, as [start] and [stop] do.
     */
    override fun latest(window: FloatArray): Long {
        val position = ring.latest(window)
        if (position != lastPosition) {
            lastPosition = position
            lastMoved = TimeSource.Monotonic.markNow()
        } else if (engine != null && !isLossReported && lastMoved.elapsedNow() >= STALL_REOPEN) {
            isLossReported = true
            listener?.onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
        }
        return position
    }

    override fun stop() {
        val engine = engine ?: return
        this.engine = null
        listener = null
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
        val STALL_REOPEN = 1.seconds
    }
}
