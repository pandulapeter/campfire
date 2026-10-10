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

import com.pandulapeter.campfire.tuner.api.Pitch
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToneSynthesizerTest {

    private val notes = InstrumentTuning.entries.flatMap { it.strings }.toSet() + 69

    @Test
    fun `the loop has no seam`() = notes.forEach { note ->
        val loop = ToneSynthesizer.loopOf(Pitch.frequencyOf(note, 440), SAMPLE_RATE)
        val largestStep = (1 until loop.size).maxOf { abs(loop[it] - loop[it - 1]) }
        assertTrue(abs(loop.first() - loop.last()) <= largestStep, "note $note")
    }

    @Test
    fun `the loop is about a second long and never reaches full scale`() = notes.forEach { note ->
        val loop = ToneSynthesizer.loopOf(Pitch.frequencyOf(note, 440), SAMPLE_RATE)
        assertTrue(loop.size in SAMPLE_RATE * 9 / 10..SAMPLE_RATE * 11 / 10, "note $note: ${loop.size}")
        assertTrue(loop.maxOf { abs(it.toInt()) } < Short.MAX_VALUE * 0.9, "note $note")
    }

    @Test
    fun `the tone is heard as its note`() {
        val detector = PitchDetector(SAMPLE_RATE)
        notes.forEach { note ->
            val frequency = Pitch.frequencyOf(note, 440)
            val loop = ToneSynthesizer.loopOf(frequency, SAMPLE_RATE)
            val window = FloatArray(detector.inputSize) { loop[it % loop.size] / 32_768f }
            val heard = assertNotNull(detector.detect(window, 30f, 2_100f).frequency, "note $note")
            assertTrue(abs(1_200 * log2(heard / frequency.toDouble())) < 1, "note $note: $heard Hz for $frequency Hz")
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
