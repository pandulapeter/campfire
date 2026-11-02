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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneStreamTest {

    // A loop that holds one value shows every change of gain as a change of sample, so a cut is a jump of the whole level.
    private val level = ShortArray(64) { LEVEL }
    private val other = ShortArray(64) { (-LEVEL).toShort() }

    @Test
    fun `a tone starts from silence`() {
        val stream = ToneStream(FADE_FRAMES)
        stream.play(level)
        val samples = render(stream, FADE_FRAMES * 2)
        assertTrue(abs(samples.first().toInt()) <= LEVEL / FADE_FRAMES + 1)
        assertEquals(LEVEL, samples.last())
    }

    @Test
    fun `a stop at any moment fades out and then writes silence`() = (0..FADE_FRAMES * 2).forEach { stopFrame ->
        val stream = ToneStream(FADE_FRAMES)
        stream.play(level)
        val samples = render(stream, stopFrame) + run {
            stream.stop()
            render(stream, FADE_FRAMES + CHUNK_FRAMES)
        }
        assertSmooth(samples, voices = 1, "stopped at $stopFrame")
        assertTrue(samples.takeLast(CHUNK_FRAMES).all { it == 0.toShort() }, "stopped at $stopFrame")
        assertTrue(stream.isSilent, "stopped at $stopFrame")
    }

    @Test
    fun `a tone replaced at any moment crossfades into the next`() = (0..FADE_FRAMES * 2).forEach { replaceFrame ->
        val stream = ToneStream(FADE_FRAMES)
        stream.play(level)
        val samples = render(stream, replaceFrame) + run {
            stream.play(other)
            render(stream, replaceFrame)
        } + run {
            // A third tone over one still fading out, and a stop over both.
            stream.play(level)
            render(stream, FADE_FRAMES / 3)
        } + run {
            stream.stop()
            render(stream, FADE_FRAMES + CHUNK_FRAMES)
        }
        assertSmooth(samples, voices = 3, "replaced at $replaceFrame")
        assertTrue(stream.isSilent, "replaced at $replaceFrame")
    }

    /** Renders [frames] samples in chunks, as an output does, so that a change can fall in the middle of one. */
    private fun render(stream: ToneStream, frames: Int): List<Short> {
        val buffer = ShortArray(CHUNK_FRAMES)
        return buildList {
            var left = frames
            while (left > 0) {
                val chunk = minOf(left, CHUNK_FRAMES)
                stream.render(buffer, chunk)
                addAll(buffer.take(chunk))
                left -= chunk
            }
        }
    }

    /** No two neighbouring samples further apart than [voices] fading at once can take them, from silence included. */
    private fun assertSmooth(samples: List<Short>, voices: Int, message: String) {
        val largestStep = voices * (LEVEL / FADE_FRAMES + 1)
        (listOf(0.toShort()) + samples).zipWithNext().forEachIndexed { index, (before, after) ->
            assertTrue(abs(after - before) <= largestStep, "$message: frame $index went from $before to $after")
        }
    }

    private companion object {
        const val LEVEL: Short = 20_000
        const val FADE_FRAMES = 48
        const val CHUNK_FRAMES = 20
    }
}
