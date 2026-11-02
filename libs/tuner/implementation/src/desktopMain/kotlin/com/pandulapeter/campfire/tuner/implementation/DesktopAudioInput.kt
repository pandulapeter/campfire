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

import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

/**
 * A `TargetDataLine` of 16-bit mono at 48 kHz, read by a daemon thread of its own into a [SampleRing].
 *
 * The JVM cannot ask macOS or Windows whether it may record, and a refusal there arrives as digital silence rather
 * than as an error, which the engine reports as `SILENT`. macOS answers its own prompt only after the line is already
 * open, and a line opened before the answer stays silent, so a line that has heard nothing but zeros for a few seconds
 * is closed and opened again.
 */
@Single
internal class DesktopAudioInput : AudioInput {

    private val ring = SampleRing(SampleRing.CAPACITY)

    private val current = AtomicReference<TargetDataLine?>(null)

    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        stop()
        val info = DataLine.Info(TargetDataLine::class.java, FORMAT)
        if (!AudioSystem.isLineSupported(info)) return AudioInputStart.Refused(TunerStopReason.NO_MICROPHONE)
        val line = try {
            open()
        } catch (_: LineUnavailableException) {
            return AudioInputStart.Refused(TunerStopReason.MICROPHONE_BUSY)
        } catch (_: Exception) {
            return AudioInputStart.Refused(TunerStopReason.FAILED)
        }
        ring.clear()
        current.set(line)
        Thread({ capture(line, listener) }, "Tuner").apply {
            isDaemon = true
            start()
        }
        return AudioInputStart.Started(SAMPLE_RATE)
    }

    private fun open() = (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, FORMAT)) as TargetDataLine).apply {
        open(FORMAT, CHUNK_FRAMES * 2 * 4)
        start()
    }

    private fun capture(first: TargetDataLine, listener: AudioInputListener) {
        var line = first
        val bytes = ByteArray(CHUNK_FRAMES * 2)
        val samples = ShortArray(CHUNK_FRAMES)
        var silentFrames = 0
        try {
            while (current.get() === line) {
                val read = line.read(bytes, 0, bytes.size)
                if (current.get() !== line) break
                if (read <= 0) {
                    listener.onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
                    break
                }
                val frames = read / 2
                var isSilent = true
                for (index in 0 until frames) {
                    val sample = ((bytes[index * 2 + 1].toInt() shl 8) or (bytes[index * 2].toInt() and 0xFF)).toShort()
                    samples[index] = sample
                    if (sample.toInt() != 0) isSilent = false
                }
                ring.write(samples, frames)
                silentFrames = if (isSilent) silentFrames + frames else 0
                if (silentFrames >= REOPEN_AFTER_SILENT_FRAMES) {
                    silentFrames = 0
                    line.stop()
                    line.close()
                    val reopened = open()
                    // A stop, or a stop and a new start, may have come while the line was being opened: the new line then
                    // belongs to nobody and is closed here, or it would hold the microphone with nothing reading it.
                    if (!current.compareAndSet(line, reopened)) {
                        reopened.close()
                        break
                    }
                    line = reopened
                }
            }
        } catch (_: Exception) {
            if (current.get() === line) listener.onLost(TunerStopReason.FAILED)
        } finally {
            // Whatever ended the thread, the line it ends with is closed; closing one stop() already closed is harmless.
            line.stop()
            line.close()
        }
    }

    override fun latest(window: FloatArray) = ring.latest(window)

    override fun stop() {
        val line = current.getAndSet(null) ?: return
        // Stopping first is what returns a read blocked on the line, so that the thread sees it is no longer wanted.
        line.stop()
        line.flush()
        line.close()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHUNK_FRAMES = 1_024
        const val REOPEN_AFTER_SILENT_FRAMES = SAMPLE_RATE * 3
        val FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
    }
}
