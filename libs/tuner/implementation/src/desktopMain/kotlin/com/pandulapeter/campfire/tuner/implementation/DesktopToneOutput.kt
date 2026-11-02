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
 * One `SourceDataLine` fed a [ToneStream] by a daemon thread. A tone that replaces another crossfades into it in the same
 * stream, on the same line, so two loops never sound side by side. A stop only fades the stream out: the thread keeps
 * writing until a whole line buffer of the stream's silence has been written, since the line still holds a buffer of
 * what came before when a write returns, and only then drains and closes the line. A tone asked for meanwhile joins the
 * same stream rather than opening a second line over the end of the last one.
 */
@Single
internal class DesktopToneOutput : ToneOutput {

    /** Guards [playback] and its stream, shared by the tuner's coroutine and the feed thread. */
    private val lock = Any()
    private var playback: Playback? = null

    private class Playback(val line: SourceDataLine, val stream: ToneStream, var onLost: () -> Unit)

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        val loop = render(SAMPLE_RATE)
        synchronized(lock) {
            playback?.let { playback ->
                playback.stream.play(loop)
                playback.onLost = onLost
                return true
            }
            val line = try {
                AudioSystem.getSourceDataLine(FORMAT).apply {
                    open(FORMAT, SAMPLE_RATE / 10 * 2)
                    start()
                }
            } catch (_: Exception) {
                return false
            }
            val stream = ToneStream((ToneOutput.FADE_SECONDS * SAMPLE_RATE).toInt())
            stream.play(loop)
            val playback = Playback(line, stream, onLost)
            this.playback = playback
            Thread({ feed(playback) }, "Tuner tone").apply {
                isDaemon = true
                start()
            }
            return true
        }
    }

    private fun feed(playback: Playback) {
        val samples = ShortArray(CHUNK_FRAMES)
        val bytes = ByteArray(CHUNK_FRAMES * 2)
        val tailChunks = playback.line.bufferSize / 2 / CHUNK_FRAMES + 1
        var silentChunks = 0
        var isEnded = false
        try {
            while (true) {
                isEnded = synchronized(lock) {
                    if (this.playback !== playback) return@synchronized true
                    playback.stream.render(samples, CHUNK_FRAMES)
                    silentChunks = if (playback.stream.isSilent) silentChunks + 1 else 0
                    if (silentChunks >= tailChunks) {
                        this.playback = null
                        true
                    } else {
                        false
                    }
                }
                if (isEnded) break
                for (index in 0 until CHUNK_FRAMES) {
                    val sample = samples[index].toInt()
                    bytes[index * 2] = sample.toByte()
                    bytes[index * 2 + 1] = (sample shr 8).toByte()
                }
                if (playback.line.write(bytes, 0, bytes.size) < bytes.size) break
            }
            if (isEnded) playback.line.drain()
        } catch (_: Exception) {
            // A line that fails is a lost tone, told below once the line is closed, as a short write is.
        } finally {
            playback.line.close()
        }
        if (!isEnded) lose(playback)
    }

    /** Lets the next [play] open a new line rather than feed a stream no thread renders any more. */
    private fun lose(playback: Playback) {
        val onLost = synchronized(lock) {
            if (this.playback === playback) this.playback = null
            playback.onLost.takeUnless { playback.stream.isSilent }
        }
        onLost?.invoke()
    }

    override fun stop() {
        synchronized(lock) { playback?.stream?.stop() }
    }

    private companion object {
        const val SAMPLE_RATE = ToneOutput.DEFAULT_SAMPLE_RATE
        const val CHUNK_FRAMES = 480
        val FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
    }
}
