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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The reference tones: about a second of a note, written so that the buffer loops without a seam. It holds a whole
 * number of periods, the frequency moved to the one that fits the buffer exactly, which at a second's length is never
 * more than 0.02 cents off. The fundamental comes with three falling harmonics, since a phone's speaker cannot play the
 * fundamental of a guitar's or a bass's low strings and the ear finds the pitch from the harmonics.
 */
internal object ToneSynthesizer {

    private val HARMONICS = floatArrayOf(1f, 0.5f, 0.3f, 0.2f)

    /** The peak of the loop, well under full scale so that the harmonics adding up never clip. */
    private const val PEAK = 0.6f

    fun loopOf(frequency: Float, sampleRate: Int): ShortArray {
        val periods = frequency.roundToInt().coerceAtLeast(1)
        val length = (periods * sampleRate / frequency.toDouble()).roundToInt()
        val playedFrequency = periods.toDouble() * sampleRate / length
        val audible = HARMONICS.indices.filter { (it + 1) * playedFrequency < sampleRate / 2 }
        val total = audible.sumOf { HARMONICS[it].toDouble() }
        return ShortArray(length) { frame ->
            var value = 0.0
            for (harmonic in audible) value += HARMONICS[harmonic] * sin(2 * PI * (harmonic + 1) * periods * frame / length)
            (value / total * PEAK * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
