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
import kotlin.math.cos
import kotlin.math.sin

/**
 * An in-place radix-2 FFT of [size] points (a power of two), its twiddle factors and bit reversal worked out once. Small
 * and of its own, since the autocorrelation [PitchDetector] needs thirty times a second is all it is for.
 */
internal class Fft(private val size: Int) {

    init {
        require(size > 1 && size and (size - 1) == 0) { "$size is not a power of two" }
    }

    private val cosines = FloatArray(size / 2) { cos(-2 * PI * it / size).toFloat() }
    private val sines = FloatArray(size / 2) { sin(-2 * PI * it / size).toFloat() }
    private val reversed = IntArray(size).also { table ->
        val bits = size.countTrailingZeroBits()
        for (index in 0 until size) table[index] = reverseBits(index, bits)
    }

    /** Transforms [real] and [imaginary] in place; [inverse] does the inverse transform, without the 1 / size scaling. */
    fun transform(real: FloatArray, imaginary: FloatArray, inverse: Boolean) {
        for (index in 0 until size) {
            val other = reversed[index]
            if (other > index) {
                real.swap(index, other)
                imaginary.swap(index, other)
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                for (offset in 0 until half) {
                    val twiddleReal = cosines[offset * step]
                    val twiddleImaginary = if (inverse) -sines[offset * step] else sines[offset * step]
                    val even = start + offset
                    val odd = even + half
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                }
                start += length
            }
            length *= 2
        }
    }

    private fun FloatArray.swap(first: Int, second: Int) {
        val value = this[first]
        this[first] = this[second]
        this[second] = value
    }
}

private fun reverseBits(value: Int, bits: Int): Int {
    var result = 0
    var rest = value
    repeat(bits) {
        result = (result shl 1) or (rest and 1)
        rest = rest shr 1
    }
    return result
}
