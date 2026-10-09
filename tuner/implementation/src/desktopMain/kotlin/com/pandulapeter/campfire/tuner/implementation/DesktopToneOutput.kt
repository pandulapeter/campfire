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

import org.koin.core.annotation.Single
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * A `SourceDataLine` fed the loop round and round by a daemon thread, which also writes the fades: in over the first
 * [ToneOutput.FADE_SECONDS] of a tone, and out over as much again once it is asked to stop, after which it lets the
 * line drain and closes it.
 */
@Single
internal class DesktopToneOutput : ToneOutput {

    @Volatile
    private var playing: Playing? = null

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        stop()
        val line = try {
            AudioSystem.getSourceDataLine(FORMAT).apply {
                open(FORMAT, SAMPLE_RATE / 10 * 2)
                start()
            }
        } catch (_: Exception) {
            return false
        }
        val playing = Playing(line, render(SAMPLE_RATE))
        this.playing = playing
        Thread({ feed(playing, onLost) }, "Tuner tone").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun feed(playing: Playing, onLost: () -> Unit) {
        val fadeFrames = (ToneOutput.FADE_SECONDS * SAMPLE_RATE).toInt()
        val bytes = ByteArray(CHUNK_FRAMES * 2)
        var position = 0L
        var fadeOutLeft = fadeFrames
        try {
            while (fadeOutLeft > 0) {
                val isStopping = playing.isStopping
                for (index in 0 until CHUNK_FRAMES) {
                    val frame = position + index
                    var gain = (frame.toFloat() / fadeFrames).coerceAtMost(1f)
                    if (isStopping) gain *= (fadeOutLeft-- / fadeFrames.toFloat()).coerceAtLeast(0f)
                    val sample = (playing.loop[(frame % playing.loop.size).toInt()] * gain).toInt()
                    bytes[index * 2] = sample.toByte()
                    bytes[index * 2 + 1] = (sample shr 8).toByte()
                }
                position += CHUNK_FRAMES
                if (playing.line.write(bytes, 0, bytes.size) < bytes.size) {
                    if (!playing.isStopping) onLost()
                    break
                }
            }
            playing.line.drain()
        } catch (_: Exception) {
            if (!playing.isStopping) onLost()
        } finally {
            playing.line.close()
        }
    }

    override fun stop() {
        val playing = playing ?: return
        this.playing = null
        playing.isStopping = true
    }

    private class Playing(val line: SourceDataLine, val loop: ShortArray) {
        @Volatile
        var isStopping = false
    }

    private companion object {
        const val SAMPLE_RATE = ToneOutput.DEFAULT_SAMPLE_RATE
        const val CHUNK_FRAMES = 480
        val FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
    }
}
