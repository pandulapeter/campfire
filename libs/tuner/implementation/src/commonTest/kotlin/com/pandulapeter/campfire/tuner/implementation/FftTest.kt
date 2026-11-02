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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FftTest {

    @Test
    fun `a forward and an inverse transform give back the input times its size`() {
        listOf(8, 64, 8192).forEach { size ->
            val signal = FloatArray(size) { (sin(it * 0.37) + 0.2 * cos(it * 1.3)).toFloat() }
            val real = signal.copyOf()
            val imaginary = FloatArray(size)
            val fft = Fft(size)
            fft.transform(real, imaginary, inverse = false)
            fft.transform(real, imaginary, inverse = true)
            val tolerance = 1e-5f * size
            for (index in 0 until size) {
                assertTrue(abs(real[index] - size * signal[index]) < tolerance, "size $size, real[$index] = ${real[index]}")
                assertTrue(abs(imaginary[index]) < tolerance, "size $size, imaginary[$index] = ${imaginary[index]}")
            }
        }
    }

    @Test
    fun `an impulse transforms to the direct DFT`() {
        val real = FloatArray(8).also { it[3] = 1f }
        val imaginary = FloatArray(8)
        Fft(8).transform(real, imaginary, inverse = false)
        for (k in 0 until 8) {
            val angle = -2 * PI * 3 * k / 8
            assertTrue(abs(real[k] - cos(angle).toFloat()) < 1e-6f, "real[$k] = ${real[k]}")
            assertTrue(abs(imaginary[k] - sin(angle).toFloat()) < 1e-6f, "imaginary[$k] = ${imaginary[k]}")
        }
    }

    @Test
    fun `a size that is not a power of two is refused`() {
        assertFailsWith<IllegalArgumentException> { Fft(12) }
    }
}
