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

import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ClickMixerTest {

    @Test
    fun `a click crossing a chunk boundary is the same as unchunked`() {
        val whole = ShortArray(4_800)
        ClickMixer(SAMPLE_RATE).apply {
            add(frame = 1_000, sound = MetronomeSound.COWBELL, voice = ClickVoice.ACCENT, gain = 1f)
            render(whole, whole.size)
        }
        val chunked = ShortArray(4_800)
        val chunk = ShortArray(960)
        ClickMixer(SAMPLE_RATE).apply {
            add(frame = 1_000, sound = MetronomeSound.COWBELL, voice = ClickVoice.ACCENT, gain = 1f)
            for (start in 0 until chunked.size step chunk.size) {
                render(chunk, chunk.size)
                chunk.copyInto(chunked, start)
            }
        }
        assertContentEquals(whole, chunked)
        assertTrue(whole.any { it != 0.toShort() })
    }

    @Test
    fun `a stream is the same however it is chunked`() {
        fun render(chunkSize: Int): ShortArray {
            val stream = ClickStream(SAMPLE_RATE, MetronomePattern(bpm = 300, sound = MetronomeSound.WOODBLOCK))
            val output = ShortArray(SAMPLE_RATE)
            val chunk = ShortArray(chunkSize)
            for (start in 0 until output.size step chunkSize) {
                stream.renderPcm(chunk, chunkSize)
                chunk.copyInto(output, start, 0, minOf(chunkSize, output.size - start))
            }
            return output
        }
        assertContentEquals(render(960), render(1_000))
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
