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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchDetectorTest {

    @Test
    fun `the window is about 85 ms at every rate`() {
        assertEquals(4096, PitchDetector.windowSizeFor(44_100))
        assertEquals(4096, PitchDetector.windowSizeFor(48_000))
        assertEquals(2048, PitchDetector.windowSizeFor(16_000))
    }

    @Test
    fun `a sine is read within a cent up to 1 kHz and within three cents above, at every rate`() {
        SAMPLE_RATES.forEach { sampleRate ->
            val detector = PitchDetector(sampleRate)
            (24..96).forEach { note ->
                val frequency = Pitch.frequencyOf(note, 440)
                if (frequency < PitchDetector.CHROMATIC_MIN_FREQUENCY || frequency > PitchDetector.CHROMATIC_MAX_FREQUENCY) return@forEach
                val window = TestSignals.sine(frequency, sampleRate, detector.windowSize)
                assertReads(detector, window, frequency, tolerance = if (frequency <= 1_000f) 1f else 3f, label = "sine $note at $sampleRate")
            }
        }
    }

    @Test
    fun `every string of every preset is read as a plucked string`() {
        SAMPLE_RATES.forEach { sampleRate ->
            val detector = PitchDetector(sampleRate)
            InstrumentTuning.entries.forEach { tuning ->
                val range = PitchDetector.rangeFor(tuning, 440)
                tuning.strings.forEach { note ->
                    val frequency = Pitch.frequencyOf(note, 440)
                    val window = TestSignals.pluckedString(frequency, sampleRate, detector.windowSize, seed = note, skip = sampleRate / 10)
                    val estimate = detector.detect(window, range.start, range.endInclusive)
                    assertReads(estimate, frequency, tolerance = 1f, label = "${tuning.id} $note at $sampleRate")
                }
            }
        }
    }

    @Test
    fun `a string is still read under 20 dB of noise`() {
        val sampleRate = 48_000
        val detector = PitchDetector(sampleRate)
        InstrumentTuning.GUITAR.strings.forEach { note ->
            val frequency = Pitch.frequencyOf(note, 440)
            val string = TestSignals.pluckedString(frequency, sampleRate, detector.windowSize, seed = note, skip = sampleRate / 10)
            val window = TestSignals.mixed(string, TestSignals.noiseUnder(string, decibels = 20f))
            val range = PitchDetector.rangeFor(InstrumentTuning.GUITAR, 440)
            assertReads(detector.detect(window, range.start, range.endInclusive), frequency, tolerance = 2f, label = "guitar $note in noise")
        }
    }

    @Test
    fun `a low string whose fundamental is 20 dB under its second harmonic is read at the right octave`() {
        val sampleRate = 48_000
        val detector = PitchDetector(sampleRate)
        listOf(InstrumentTuning.GUITAR to 40, InstrumentTuning.BASS to 28, InstrumentTuning.BASS_FIVE_STRING to 23, null to 40).forEach { (tuning, note) ->
            val frequency = Pitch.frequencyOf(note, 440)
            val window = TestSignals.harmonics(frequency, sampleRate, detector.windowSize, listOf(0.1f, 1f, 0.7f, 0.5f, 0.35f, 0.25f, 0.15f))
            val range = PitchDetector.rangeFor(tuning, 440)
            assertReads(detector.detect(window, range.start, range.endInclusive), frequency, tolerance = 1f, label = "weak fundamental $note")
        }
    }

    @Test
    fun `silence, noise and a chord answer nothing`() {
        val sampleRate = 48_000
        val detector = PitchDetector(sampleRate)
        val range = PitchDetector.rangeFor(null, 440)
        val silence = detector.detect(FloatArray(detector.windowSize), range.start, range.endInclusive)
        assertNull(silence.frequency)
        assertEquals(0f, silence.level)
        assertNull(detector.detect(TestSignals.noise(detector.windowSize, 0.3f), range.start, range.endInclusive).frequency)
        val chord = TestSignals.mixed(
            *listOf(48, 52, 55, 59).map { TestSignals.sine(Pitch.frequencyOf(it, 440) * 1.003f, sampleRate, detector.windowSize, 0.2f, phase = it * 0.4) }.toTypedArray(),
        )
        assertNull(detector.detect(chord, range.start, range.endInclusive).frequency)
    }

    private fun assertReads(detector: PitchDetector, window: FloatArray, frequency: Float, tolerance: Float, label: String) {
        val range = PitchDetector.rangeFor(null, 440)
        assertReads(detector.detect(window, range.start, range.endInclusive), frequency, tolerance, label)
    }

    private fun assertReads(estimate: PitchEstimate, frequency: Float, tolerance: Float, label: String) {
        val heard = assertNotNull(estimate.frequency, "$label: nothing read, clarity ${estimate.clarity}")
        val cents = 1_200 * log2(heard / frequency.toDouble())
        assertTrue(abs(cents) <= tolerance, "$label: read $heard Hz for $frequency Hz, $cents cents")
    }

    private companion object {
        val SAMPLE_RATES = listOf(16_000, 44_100, 48_000)
    }
}
