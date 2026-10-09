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
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferLoops
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.sampleRate
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.darwin.NSObjectProtocol
import platform.posix.usleep

/**
 * An `AVAudioEngine` of its own playing the loop through an `AVAudioPlayerNode`: one copy of it faded in, then the loop
 * itself scheduled to repeat, which follows it seamlessly since the loop ends where it starts. A stop turns the player's
 * volume down over a few milliseconds first.
 */
@Single
internal class IosToneOutput(private val session: IosTunerSession) : ToneOutput {

    private var engine: AVAudioEngine? = null
    private var player: AVAudioPlayerNode? = null
    private var observers = emptyList<NSObjectProtocol>()

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        stop()
        if (!session.acquire()) return false
        val sampleRate = AVAudioSession.sharedInstance().sampleRate.toInt().takeIf { it > 0 } ?: ToneOutput.DEFAULT_SAMPLE_RATE
        val loop = render(sampleRate)
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = 1u)
        val fadeFrames = (ToneOutput.FADE_SECONDS * sampleRate).toInt()
        val fadingIn = buffer(format, loop) { index -> (index.toFloat() / fadeFrames).coerceAtMost(1f) }
        val looping = buffer(format, loop) { 1f }
        val engine = AVAudioEngine()
        val player = AVAudioPlayerNode()
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, format)
        engine.prepare()
        val isStarted = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(error.ptr)
        }
        if (!isStarted) {
            session.release()
            return false
        }
        player.scheduleBuffer(fadingIn, null)
        player.scheduleBuffer(looping, null, AVAudioPlayerNodeBufferLoops, null)
        player.play()
        this.engine = engine
        this.player = player
        observers = observe(engine, onLost)
        return true
    }

    /**
     * A call, a new route and the media services being reset each stop the engine on their own; the tone is then over
     * rather than shown as sounding, which would also keep every reading hidden. The configuration change is observed on
     * this engine only, so that the input's engine changing does not end the tone by itself.
     */
    private fun observe(engine: AVAudioEngine, onLost: () -> Unit): List<NSObjectProtocol> {
        val center = NSNotificationCenter.defaultCenter
        return listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) onLost()
            },
            center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) { onLost() },
            center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) { onLost() },
        )
    }

    private fun buffer(format: AVAudioFormat, loop: ShortArray, gain: (Int) -> Float) =
        AVAudioPCMBuffer(pCMFormat = format, frameCapacity = loop.size.toUInt())!!.apply {
            val channel = floatChannelData!![0]!!
            for (index in loop.indices) channel[index] = loop[index] / SHORT_SCALE * gain(index)
            frameLength = loop.size.toUInt()
        }

    override fun stop() {
        val player = player ?: return
        this.player = null
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers = emptyList()
        repeat(FADE_STEPS) { step ->
            player.volume = 1f - (step + 1f) / FADE_STEPS
            usleep((ToneOutput.FADE_SECONDS * 1_000_000 / FADE_STEPS).toUInt())
        }
        player.stop()
        engine?.stop()
        engine = null
        session.release()
    }

    private companion object {
        const val SHORT_SCALE = 32_768f
        const val FADE_STEPS = 5
    }
}
