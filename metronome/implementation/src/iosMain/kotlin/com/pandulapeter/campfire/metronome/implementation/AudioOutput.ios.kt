/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pandulapeter.campfire.metronome.implementation

import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlin.concurrent.Volatile
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.outputLatency
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.Foundation.NSError
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

/**
 * An `AVAudioEngine` playing through an `AVAudioPlayerNode`, fed with ~20 ms buffers that a thread of the output's own
 * mixes, [BUFFER_COUNT] of them in flight: a buffer is mixed again only once the player says it has consumed it, and
 * the completion handler only signals the thread rather than mixing on the system's queue. Not an `AVAudioSourceNode`
 * render block, which would run Kotlin/Native on the real-time audio thread, where a collection is a gap in the sound.
 *
 * The session is the playback category, so that the silent switch does not silence a metronome, and is active only
 * while a click plays, which is what the audio background mode is allowed for. A preview takes the ambient category
 * instead, which neither stops the user's music nor needs the background. The interruption, route change and reset
 * observers are here rather than in the Now Playing shell, so that the click stops whether or not any UI is around.
 */
@Single
internal class IosAudioOutput : AudioOutput {

    private var engine: AVAudioEngine? = null

    @Volatile
    private var player: AVAudioPlayerNode? = null

    @Volatile
    private var gapTracker: PlaybackGapTracker? = null
    /** The rate the session reports it runs at, [AudioOutput.DEFAULT_SAMPLE_RATE] only for one that reports none. */
    private var sampleRate = AudioOutput.DEFAULT_SAMPLE_RATE
    private var observers = emptyList<NSObjectProtocol>()

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        stop()
        val session = AVAudioSession.sharedInstance()
        val isActive = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            session.setCategory(if (isPreview) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback, error.ptr) &&
                session.setActive(true, error.ptr)
        }
        if (!isActive) return AudioOutputStart.Refused(MetronomeStopReason.AUDIO_REFUSED)
        sampleRate = session.sampleRate.toInt().takeIf { it > 0 } ?: AudioOutput.DEFAULT_SAMPLE_RATE
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
            deactivateSession()
            return AudioOutputStart.Unavailable
        }
        val gapTracker = PlaybackGapTracker()
        this.engine = engine
        this.gapTracker = gapTracker
        this.player = player
        observers = observe(engine, listener)
        val stream = createStream(sampleRate)
        val frames = AudioOutput.chunkFrames(sampleRate)
        val buffers = List(BUFFER_COUNT) { AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt())!! }
        val samples = ShortArray(frames)
        // Created empty, never with a count: libdispatch ends the process when a semaphore is released holding less than
        // it was created with, and the thread leaves with one wait taken. It needs none to begin with either, since
        // every buffer is in flight before the thread starts, and each one consumed is what signals it.
        val freeBuffers = dispatch_semaphore_create(0)!!
        // Every buffer is queued before the player plays, so that the player's time 0 is the stream's frame 0 rather than
        // the moment the thread got round to its first render. Here and not on the feed thread: a play() made after a
        // stop() or a configuration change stopped the engine raises an Objective-C exception, and only this coroutine
        // runs start and stop.
        buffers.forEach { buffer -> scheduleNext(player, stream, samples, buffer, frames, freeBuffers) }
        player.play()
        NSThread {
            feed(player, stream, buffers, samples, frames, freeBuffers, gapTracker)
        }.apply {
            qualityOfService = NSQualityOfServiceUserInteractive
            name = "Metronome"
            start()
        }
        return AudioOutputStart.Started()
    }

    private fun feed(
        player: AVAudioPlayerNode,
        stream: ClickStream,
        buffers: List<AVAudioPCMBuffer>,
        samples: ShortArray,
        frames: Int,
        freeBuffers: dispatch_semaphore_t,
        gapTracker: PlaybackGapTracker,
    ) {
        var next = 0
        var scheduledFrames = buffers.size.toLong() * frames
        while (true) {
            dispatch_semaphore_wait(freeBuffers, DISPATCH_TIME_FOREVER)
            if (this.player !== player) return
            // A queue that ran dry adds its gap to the player's time (see PlaybackGapTracker). Read a render cycle late,
            // so a gap can come out a few milliseconds short - a beat released a little early at worst, never late.
            gapTracker.onBufferFreed(scheduledFrames, playerSampleTime(player))
            scheduleNext(player, stream, samples, buffers[next], frames, freeBuffers)
            scheduledFrames += frames
            next = (next + 1) % buffers.size
        }
    }

    private fun scheduleNext(
        player: AVAudioPlayerNode,
        stream: ClickStream,
        samples: ShortArray,
        buffer: AVAudioPCMBuffer,
        frames: Int,
        freeBuffers: dispatch_semaphore_t,
    ) {
        stream.renderPcm(samples, frames)
        val channel = buffer.floatChannelData!![0]!!
        for (index in 0 until frames) channel[index] = samples[index] / SHORT_SCALE
        buffer.frameLength = frames.toUInt()
        player.scheduleBuffer(buffer) { dispatch_semaphore_signal(freeBuffers) }
    }

    override fun heardFrame(): Long {
        val player = player ?: return -1L
        val gapTracker = gapTracker ?: return -1L
        val sampleTime = playerSampleTime(player) ?: return -1L
        return gapTracker.heardFrame(sampleTime, AudioClock.latencyFrames(AVAudioSession.sharedInstance().outputLatency, sampleRate))
    }

    private fun playerSampleTime(player: AVAudioPlayerNode): Long? {
        val nodeTime = player.lastRenderTime ?: return null
        return player.playerTimeForNodeTime(nodeTime)?.sampleTime
    }

    override fun stop() {
        val player = player ?: return
        this.player = null
        gapTracker = null
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers = emptyList()
        // Stopping the player drops what is scheduled and calls every buffer's completion handler, which is what wakes
        // a thread waiting for a free buffer, so that it sees it is no longer wanted.
        player.stop()
        engine?.stop()
        engine = null
        deactivateSession()
    }

    private fun observe(engine: AVAudioEngine, listener: AudioOutputListener): List<NSObjectProtocol> {
        val center = NSNotificationCenter.defaultCenter
        return listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                if (type == AVAudioSessionInterruptionTypeBegan) listener.onLost(MetronomeStopReason.AUDIO_INTERRUPTED)
            },
            center.addObserverForName(AVAudioSessionRouteChangeNotification, null, null) { notification ->
                val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
                if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) listener.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
            },
            // A new route (headphones plugged in) stops the engine on its own, and the services being reset takes it
            // away altogether; either way what was scheduled is gone, so the click ends and says why rather than going
            // quiet while it still shows as playing.
            center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) {
                listener.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
            },
            center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) {
                listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
            },
        )
    }

    private fun deactivateSession() {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)
        }
    }

    private companion object {
        const val BUFFER_COUNT = 5
        const val SHORT_SCALE = 32_768f
    }
}
