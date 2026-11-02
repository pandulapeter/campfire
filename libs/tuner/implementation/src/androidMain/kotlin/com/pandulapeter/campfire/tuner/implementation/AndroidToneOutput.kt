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
import android.os.SystemClock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * A static `AudioTrack` looping the one buffer for ever (`setLoopPoints`), at the device's own output rate, with a
 * `VolumeShaper` for the fades. Tones take transient audio focus, as a notification sound does, held for as long as one
 * follows another and given back when they stop or it is lost; a loss stops the tone rather than leaving it to come back
 * on its own.
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

    /**
     * The onLost of the tone sounding now, which the focus listener calls rather than that of the tone that asked for the
     * focus, since the engine ignores a replaced tone's loss. Set by [play] on the engine's thread, read on the main looper.
     */
    @Volatile
    private var onLost: () -> Unit = {}

    @Volatile
    private var isFocusLost = false

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        // The loss the engine posts to its own thread can arrive after the next note's play, and reach a replaced tone's
        // onLost, which it ignores. The listener marks the loss before reading onLost, and this sets onLost before reading
        // the mark, so either the new tone's onLost is called or the focus is asked for again here, which a call refuses.
        this.onLost = onLost
        fadeOutTrack()
        // A held focus is kept from one tone to the next (a scale): giving it back between notes resumes and pauses
        // another app's music.
        if (focusRequest == null || isFocusLost) {
            abandonFocus()
            isFocusLost = false
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(
                    { change ->
                        if (change < 0) {
                            isFocusLost = true
                            this.onLost()
                        }
                    },
                    handler,
                )
                .build()
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            this.focusRequest = focusRequest
        }
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
        fadeOutTrack()
        abandonFocus()
    }

    private fun fadeOutTrack() {
        val track = track ?: return
        this.track = null
        val fadeOut = try {
            track.createVolumeShaper(fade(floatArrayOf(1f, 0f))).also { it.apply(VolumeShaper.Operation.PLAY) }
        } catch (_: IllegalStateException) {
            null
        }
        releaseOnceSilent(track, fadeOut, SystemClock.uptimeMillis() + RELEASE_TIMEOUT_MILLIS)
    }

    /**
     * The fade is applied by the mixer as it mixes, which on the deep buffer output a music track is routed to runs tens
     * of milliseconds ahead in large periods, so a fixed wait of the fade's length can stop the track before the faded
     * frames were ever mixed, and that cut at full amplitude is a loud pop. The shaper's volume is the one the mixer last
     * applied, so the track is only stopped once it reads silence; frames mixed after that are silent whatever stopping
     * does to them. A track the mixer never reaches (one the system paused) is released after a timeout, and one whose
     * fade cannot be read, at once: nothing is fading it, so waiting would only keep the tone sounding at full volume.
     */
    private fun releaseOnceSilent(track: AudioTrack, fadeOut: VolumeShaper?, deadlineMillis: Long) {
        handler.postDelayed({
            val volume = try {
                fadeOut?.volume
            } catch (_: IllegalStateException) {
                null
            }
            if (volume != null && volume > 0f && SystemClock.uptimeMillis() < deadlineMillis) {
                releaseOnceSilent(track, fadeOut, deadlineMillis)
            } else {
                try {
                    track.stop()
                } catch (_: IllegalStateException) {
                }
                fadeOut?.close()
                track.release()
            }
        }, FADE_MILLIS)
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
        const val RELEASE_TIMEOUT_MILLIS = 1_000L
    }
}
