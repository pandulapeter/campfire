/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A fourth-order Butterworth high-pass as two biquads, run over one input at a time from rest. */
internal class HighPassFilter(sampleRate: Int, cutoff: Float) {

    private val sections = listOf(0.5412, 1.3066).map { quality -> Section(sampleRate, cutoff, quality) }

    /** Filters the first [length] samples of [samples] in place. */
    fun filter(samples: FloatArray, length: Int) = sections.forEach { it.filter(samples, length) }

    private class Section(sampleRate: Int, cutoff: Float, quality: Double) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        init {
            val omega = 2 * PI * cutoff / sampleRate
            val alpha = sin(omega) / (2 * quality)
            val cosine = cos(omega)
            val a0 = 1 + alpha
            b0 = (1 + cosine) / 2 / a0
            b1 = -(1 + cosine) / a0
            b2 = b0
            a1 = -2 * cosine / a0
            a2 = (1 - alpha) / a0
        }

        fun filter(samples: FloatArray, length: Int) {
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            for (index in 0 until length) {
                val x = samples[index].toDouble()
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x
                y2 = y1
                y1 = y
                samples[index] = y.toFloat()
            }
        }
    }
}
