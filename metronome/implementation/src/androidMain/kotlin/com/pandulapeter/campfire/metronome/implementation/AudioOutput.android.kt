/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.implementation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * An `AudioTrack` in streaming mode at the device's own output rate, so nothing is resampled, fed from a thread at
 * urgent audio priority. The default performance mode rather than the low-latency one: latency is only heard when the
 * click starts and stops, and the fast path costs underruns and battery with the screen off, which a click that has
 * to carry on in a pocket cannot afford. The track's buffer holds at least [AudioOutput.QUEUED_SECONDS], and a write
 * blocks while it is full, which is what paces the thread.
 *
 * Audio focus and the headphones being pulled belong here rather than to the foreground service, so that a click
 * cannot start without focus (a call in progress refuses it) and stops on a loss whether or not the service is up.
 * A loss of any kind stops it - a click that comes back on its own after a call is a surprise - and ducking is left to
 * the system.
 */
@Single
internal class AndroidAudioOutput(
    // Provided rather than declared: the context is what the Android app shell hands to Koin as it starts, which no
    // shared module can see.
    @Provided context: Context,
) : AudioOutput {

    private val context = context.applicationContext
    private val audioManager = this.context.getSystemService(AudioManager::class.java)
    private val timestamp = AudioTimestamp()

    @Volatile
    private var track: AudioTrack? = null
    /** The rate the device reports it mixes at, [AudioOutput.DEFAULT_SAMPLE_RATE] only for one that reports none. */
    private var sampleRate = AudioOutput.DEFAULT_SAMPLE_RATE
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        stop()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // A preview takes the audio only for a moment, the way a notification sound does, and lets a playing song duck
        // under it rather than stopping it.
        val focusRequest = AudioFocusRequest.Builder(if (isPreview) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    listener.onLost(MetronomeStopReason.AUDIO_INTERRUPTED)
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return AudioOutputStart.Refused(MetronomeStopReason.AUDIO_REFUSED)
        }
        this.focusRequest = focusRequest
        sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: AudioOutput.DEFAULT_SAMPLE_RATE
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(
                    maxOf(
                        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                        AudioOutput.queuedFrames(sampleRate) * AudioOutput.BYTES_PER_FRAME,
                    )
                )
                .build()
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        }
        if (track == null) {
            abandonFocus()
            return AudioOutputStart.Unavailable
        }
        if (!isPreview) registerNoisyReceiver(listener)
        this.track = track
        val stream = createStream(sampleRate)
        track.play()
        Thread({ feed(track, stream, listener) }, "Metronome").start()
        return AudioOutputStart.Started()
    }

    private fun feed(track: AudioTrack, stream: ClickStream, listener: AudioOutputListener) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frames = AudioOutput.chunkFrames(sampleRate)
        val samples = ShortArray(frames)
        try {
            while (this.track === track) {
                stream.renderPcm(samples, frames)
                if (track.write(samples, 0, frames) < 0 && this.track === track) {
                    listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
                    break
                }
            }
        } catch (_: Exception) {
            if (this.track === track) listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
        } finally {
            // Released by the thread that writes to it rather than by stop(), since releasing a track another thread
            // is writing to is undefined.
            track.release()
        }
    }

    override fun heardFrame(): Long {
        val track = track ?: return -1L
        return try {
            if (track.getTimestamp(timestamp)) {
                AudioClock.extrapolatedFrame(timestamp.framePosition, timestamp.nanoTime, System.nanoTime(), sampleRate)
            } else {
                track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            }
        } catch (_: IllegalStateException) {
            -1L
        }
    }

    override fun stop() {
        val track = track ?: return
        this.track = null
        // Pausing and flushing drops what is queued rather than playing it out, and is also what makes room for a
        // write blocked on a full buffer to return, so that the thread sees it is no longer wanted.
        try {
            track.pause()
            track.flush()
        } catch (_: IllegalStateException) {
        }
        noisyReceiver?.let(context::unregisterReceiver)
        noisyReceiver = null
        abandonFocus()
    }

    private fun registerNoisyReceiver(listener: AudioOutputListener) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) listener.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        noisyReceiver = receiver
    }

    private fun abandonFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }
}
