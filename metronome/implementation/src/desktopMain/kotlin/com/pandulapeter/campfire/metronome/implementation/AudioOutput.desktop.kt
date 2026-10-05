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

import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import org.koin.core.annotation.Single
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * A `SourceDataLine` of 16-bit mono at 48 kHz, fed from a daemon thread of its own. The line's buffer is the queue:
 * a write blocks while it holds [AudioOutput.QUEUED_SECONDS] of sound, which is what paces the thread. A computer
 * with no audio device (a Linux box without a sound server) cannot open one, and the click runs silently instead.
 */
@Single
internal class DesktopAudioOutput : AudioOutput {

    @Volatile
    private var line: SourceDataLine? = null

    @Volatile
    private var thread: Thread? = null

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        stop()
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val line = try {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, (AudioOutput.QUEUED_SECONDS * SAMPLE_RATE).toInt() * format.frameSize)
                start()
            }
        } catch (_: Exception) {
            return AudioOutputStart.Unavailable
        }
        this.line = line
        val stream = createStream(SAMPLE_RATE)
        thread = Thread({ feed(line, stream, listener) }, "Metronome").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
        return AudioOutputStart.Started()
    }

    private fun feed(line: SourceDataLine, stream: ClickStream, listener: AudioOutputListener) {
        val frames = (AudioOutput.CHUNK_SECONDS * SAMPLE_RATE).toInt()
        val samples = ShortArray(frames)
        val bytes = ByteArray(frames * 2)
        try {
            while (this.line === line) {
                stream.renderPcm(samples, frames)
                for (index in 0 until frames) {
                    val sample = samples[index].toInt()
                    bytes[index * 2] = sample.toByte()
                    bytes[index * 2 + 1] = (sample shr 8).toByte()
                }
                line.write(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            if (this.line === line) listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
        }
    }

    override fun heardFrame() = line?.longFramePosition ?: -1L

    override fun stop() {
        val line = line ?: return
        this.line = null
        // Stopping and flushing first is what returns a write blocked on a full buffer, so that the thread sees it is
        // no longer wanted rather than playing out what was queued.
        line.stop()
        line.flush()
        line.close()
        thread = null
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
