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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * An `AudioRecord` of 16-bit mono at 48 kHz, read by a thread of its own into a [SampleRing]. The source is
 * `UNPROCESSED` where the device says it has one and `VOICE_RECOGNITION` elsewhere, the two that leave out the gain
 * control and the noise suppression a held note would be removed by. No Bluetooth SCO is ever started, so a headset
 * never becomes the input.
 *
 * It never asks for the permission: without it the start is refused before anything is opened, since the request
 * belongs to the button on the page. A call silencing the input (Android 10 and above say so) stops it as busy.
 */
@Single
internal class AndroidAudioInput(
    // Provided rather than declared: the context is what the Android app shell hands to Koin as it starts, which no
    // shared module can see.
    @Provided context: Context,
) : AudioInput {

    private val context = context.applicationContext
    private val audioManager = this.context.getSystemService(AudioManager::class.java)
    private val ring = SampleRing(SampleRing.CAPACITY)

    @Volatile
    private var record: AudioRecord? = null
    private var callback: AudioManager.AudioRecordingCallback? = null

    @SuppressLint("MissingPermission")
    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        stop()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return AudioInputStart.Refused(TunerStopReason.PERMISSION_DENIED)
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            return AudioInputStart.Refused(TunerStopReason.NO_MICROPHONE)
        }
        val source = if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), CHUNK_FRAMES * 2 * 4))
                .build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        } ?: return AudioInputStart.Refused(TunerStopReason.FAILED)
        try {
            record.startRecording()
        } catch (_: IllegalStateException) {
            record.release()
            return AudioInputStart.Refused(TunerStopReason.FAILED)
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return AudioInputStart.Refused(TunerStopReason.MICROPHONE_BUSY)
        }
        ring.clear()
        this.record = record
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) registerSilencing(record, listener)
        Thread({ capture(record, listener) }, "Tuner").start()
        return AudioInputStart.Started(SAMPLE_RATE)
    }

    private fun capture(record: AudioRecord, listener: AudioInputListener) {
        val samples = ShortArray(CHUNK_FRAMES)
        try {
            while (this.record === record) {
                val read = record.read(samples, 0, samples.size)
                if (this.record !== record) break
                if (read < 0) {
                    listener.onLost(if (read == AudioRecord.ERROR_DEAD_OBJECT) TunerStopReason.MICROPHONE_DISCONNECTED else TunerStopReason.FAILED)
                    break
                }
                ring.write(samples, read)
            }
        } catch (_: Exception) {
            if (this.record === record) listener.onLost(TunerStopReason.FAILED)
        } finally {
            // Released by the thread that reads from it rather than by stop(), since releasing a record another thread
            // is reading from is undefined.
            record.release()
        }
    }

    @SuppressLint("NewApi")
    private fun registerSilencing(record: AudioRecord, listener: AudioInputListener) {
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                val ours = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId } ?: return
                if (ours.isClientSilenced && this@AndroidAudioInput.record === record) listener.onLost(TunerStopReason.MICROPHONE_BUSY)
            }
        }
        audioManager.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
        this.callback = callback
    }

    override fun latest(window: FloatArray) = ring.latest(window)

    override fun stop() {
        val record = record ?: return
        this.record = null
        callback?.let(audioManager::unregisterAudioRecordingCallback)
        callback = null
        // Stopping is what returns a read blocked on the record, so that the thread sees it is no longer wanted.
        try {
            record.stop()
        } catch (_: IllegalStateException) {
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHUNK_FRAMES = 1_024
    }
}
