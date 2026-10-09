/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Handler
import android.os.Looper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * A static `AudioTrack` looping the one buffer for ever (`setLoopPoints`), at the device's own output rate, with a
 * `VolumeShaper` for the fades. A tone takes transient audio focus, as a notification sound does, and a loss of it
 * stops the tone rather than leaving it to come back on its own.
 */
@Single
internal class AndroidToneOutput(
    // Provided rather than declared: the context is what the Android app shell hands to Koin as it starts, which no
    // shared module can see.
    @Provided context: Context,
) : ToneOutput {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        stop()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) onLost() }, handler)
            .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        this.focusRequest = focusRequest
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: ToneOutput.DEFAULT_SAMPLE_RATE
        val loop = render(sampleRate)
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
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(loop.size * 2)
                .build()
                .also { track ->
                    track.write(loop, 0, loop.size)
                    track.setLoopPoints(0, loop.size, -1)
                }
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        }
        if (track == null) {
            abandonFocus()
            return false
        }
        track.createVolumeShaper(fade(floatArrayOf(0f, 1f))).apply(VolumeShaper.Operation.PLAY)
        track.play()
        this.track = track
        return true
    }

    override fun stop() {
        val track = track ?: return
        this.track = null
        abandonFocus()
        try {
            track.createVolumeShaper(fade(floatArrayOf(1f, 0f))).apply(VolumeShaper.Operation.PLAY)
        } catch (_: IllegalStateException) {
        }
        handler.postDelayed({
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }, FADE_MILLIS * 2)
    }

    private fun fade(volumes: FloatArray) = VolumeShaper.Configuration.Builder()
        .setCurve(floatArrayOf(0f, 1f), volumes)
        .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
        .setDuration(FADE_MILLIS)
        .build()

    private fun abandonFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private companion object {
        val FADE_MILLIS = (ToneOutput.FADE_SECONDS * 1_000).toLong()
    }
}
