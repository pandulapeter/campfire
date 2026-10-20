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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.outputLatency
import platform.AVFAudio.sampleRate
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSQualityOfServiceUserInteractive
import platform.Foundation.NSThread
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_t
import platform.darwin.dispatch_semaphore_wait
import kotlin.math.ceil

/**
 * An `AVAudioEngine` of its own playing a [ToneStream] through an `AVAudioPlayerNode`, fed with short buffers by a thread
 * of the output's own, [BUFFER_COUNT] of them in flight, as the metronome feeds its player (and not from an
 * `AVAudioSourceNode` render block, for the same reason: Kotlin/Native on the real-time thread).
 *
 * Every fade is written into the samples, since the player's volume is applied once per render cycle, which is longer
 * than a fade, and a stop that only turned it down could be heard as a step. A tone that replaces another crossfades into
 * it in the same stream, on the same engine. A stop only fades the stream out: the thread keeps feeding silence until
 * all of the tone has been played and the output's latency has passed, and only then stops the engine and gives the
 * session back, so that nothing audible is ever cut off. A new tone arriving meanwhile goes into the same stream.
 */
@Single
internal class IosToneOutput(private val session: IosTunerSession) : ToneOutput {

    /** Guards [playback] and its stream, shared by the tuner's coroutine, the feed thread and the system's notifications. */
    private val lock = NSLock()
    private var playback: Playback? = null

    private class Playback(
        val engine: AVAudioEngine,
        val player: AVAudioPlayerNode,
        val stream: ToneStream,
        val sampleRate: Int,
        var onLost: () -> Unit,
    ) {
        var observers = emptyList<NSObjectProtocol>()
    }

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        val sampleRate = locked { playback?.sampleRate }
        if (sampleRate != null) {
            val loop = render(sampleRate)
            val isPlaying = locked {
                playback?.takeIf { it.sampleRate == sampleRate }?.let { playback ->
                    playback.stream.play(loop)
                    playback.onLost = onLost
                    true
                } ?: false
            }
            if (isPlaying) return true
        }
        return start(render, onLost)
    }

    private fun start(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        if (!session.acquire()) return false
        val sampleRate = AVAudioSession.sharedInstance().sampleRate.toInt().takeIf { it > 0 } ?: ToneOutput.DEFAULT_SAMPLE_RATE
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = 1u)
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
        val stream = ToneStream((ToneOutput.FADE_SECONDS * sampleRate).toInt())
        stream.play(render(sampleRate))
        val playback = Playback(engine, player, stream, sampleRate, onLost)
        val frames = (sampleRate * CHUNK_SECONDS).toInt()
        val buffers = List(BUFFER_COUNT) { AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt()) }
        val samples = ShortArray(frames)
        // Created empty, as the metronome's is: libdispatch ends the process when a semaphore is released holding less
        // than it was created with, and every buffer consumed is what signals it.
        val freeBuffers = dispatch_semaphore_create(0)!!
        // Queued before the player plays and before anyone else can see the stream, so that this needs no lock yet.
        buffers.forEach { buffer -> scheduleNext(playback, samples, buffer, frames, freeBuffers) }
        player.play()
        locked { this.playback = playback }
        playback.observers = observe(playback)
        NSThread {
            feed(playback, buffers, samples, frames, freeBuffers)
        }.apply {
            qualityOfService = NSQualityOfServiceUserInteractive
            name = "Tuner tone"
            start()
        }
        return true
    }

    private fun feed(
        playback: Playback,
        buffers: List<AVAudioPCMBuffer>,
        samples: ShortArray,
        frames: Int,
        freeBuffers: dispatch_semaphore_t,
    ) {
        // Once every buffer in flight is silence, each one more consumed is a buffer's length of silence played; the tail
        // covers the output's latency, so the last of the tone has left the speaker before the engine stops.
        val tailBuffers = ceil(AVAudioSession.sharedInstance().outputLatency / CHUNK_SECONDS).toInt() + 1
        var next = 0
        var silentBuffers = 0
        while (true) {
            dispatch_semaphore_wait(freeBuffers, DISPATCH_TIME_FOREVER)
            val isEnded = locked {
                when {
                    this.playback !== playback -> true
                    playback.stream.isSilent && silentBuffers >= BUFFER_COUNT + tailBuffers -> {
                        this.playback = null
                        tearDown(playback)
                        true
                    }
                    else -> {
                        silentBuffers = if (playback.stream.isSilent) silentBuffers + 1 else 0
                        scheduleNext(playback, samples, buffers[next], frames, freeBuffers)
                        false
                    }
                }
            }
            if (isEnded) return
            next = (next + 1) % buffers.size
        }
    }

    private fun scheduleNext(
        playback: Playback,
        samples: ShortArray,
        buffer: AVAudioPCMBuffer,
        frames: Int,
        freeBuffers: dispatch_semaphore_t,
    ) {
        playback.stream.render(samples, frames)
        val channel = buffer.floatChannelData!![0]!!
        for (index in 0 until frames) channel[index] = samples[index] / SHORT_SCALE
        buffer.frameLength = frames.toUInt()
        playback.player.scheduleBuffer(buffer) { dispatch_semaphore_signal(freeBuffers) }
    }

    override fun stop() {
        locked { playback?.stream?.stop() }
    }

    /**
     * A call, a new route and the media services being reset each stop the engine on their own, which nothing here can
     * soften; the tone is then over rather than shown as sounding, which would also keep every reading hidden. The
     * configuration change is observed on this engine only, so that the input's engine changing does not end the tone.
     */
    private fun observe(playback: Playback): List<NSObjectProtocol> {
        val center = NSNotificationCenter.defaultCenter
        return listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) lose(playback)
            },
            center.addObserverForName(AVAudioEngineConfigurationChangeNotification, playback.engine, null) { lose(playback) },
            center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) { lose(playback) },
        )
    }

    private fun lose(playback: Playback) {
        val onLost = locked {
            if (this.playback !== playback) return@locked null
            this.playback = null
            tearDown(playback)
            playback.onLost
        }
        onLost?.invoke()
    }

    /**
     * Stopping the player calls every scheduled buffer's completion handler, which is what wakes a feed thread waiting for
     * a free buffer, so that it sees it is no longer wanted.
     */
    private fun tearDown(playback: Playback) {
        playback.observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        playback.observers = emptyList()
        playback.player.stop()
        playback.engine.stop()
        session.release()
    }

    private inline fun <T> locked(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    private companion object {
        const val SHORT_SCALE = 32_768f
        const val CHUNK_SECONDS = 0.02
        const val BUFFER_COUNT = 3
    }
}
