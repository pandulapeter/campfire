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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SampleRingTest {

    @Test
    fun `nothing is handed out before a whole window has been written`() {
        val ring = SampleRing(16)
        val window = FloatArray(4)
        assertEquals(AudioInput.NO_WINDOW, ring.latest(window))
        ring.write(FloatArray(3) { it.toFloat() }, 3)
        assertEquals(AudioInput.NO_WINDOW, ring.latest(window))
    }

    @Test
    fun `the latest window is right across many wraparounds`() {
        val ring = SampleRing(16)
        val window = FloatArray(4)
        var written = 0
        listOf(3, 5, 7, 2, 9, 4, 6, 1, 11, 8).forEachIndexed { index, chunk ->
            ring.write(FloatArray(chunk) { (written + it).toFloat() }, chunk)
            written += chunk
            if (index > 0) {
                assertEquals(written.toLong(), ring.latest(window))
                assertContentEquals(FloatArray(4) { (written - 4 + it).toFloat() }, window)
            }
        }
    }

    @Test
    fun `16-bit samples are written at full scale`() {
        val ring = SampleRing(16)
        val window = FloatArray(2)
        ring.write(shortArrayOf(16_384, -32_768, 7), 2)
        assertEquals(2L, ring.latest(window))
        assertContentEquals(floatArrayOf(0.5f, -1f), window)
    }

    @Test
    fun `clear starts over`() {
        val ring = SampleRing(16)
        val window = FloatArray(4)
        ring.write(FloatArray(8), 8)
        ring.clear()
        assertEquals(AudioInput.NO_WINDOW, ring.latest(window))
    }

    @Test
    fun `a window handed out again has the same position`() {
        val ring = SampleRing(16)
        val window = FloatArray(4)
        ring.write(FloatArray(6), 6)
        assertEquals(6L, ring.latest(window))
        assertEquals(6L, ring.latest(window))
        ring.write(FloatArray(2), 2)
        assertEquals(8L, ring.latest(window))
    }
}
