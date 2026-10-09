/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchTest {

    @Test
    fun `the reference pitch is the frequency of A4 and an octave doubles it`() {
        assertEquals(440f, Pitch.frequencyOf(69, 440), 0.001f)
        assertEquals(880f, Pitch.frequencyOf(81, 440), 0.001f)
        assertEquals(82.407f, Pitch.frequencyOf(40, 440), 0.001f)
        assertEquals(432f, Pitch.frequencyOf(69, 432), 0.001f)
    }

    @Test
    fun `a frequency is read as its nearest semitone and the cents it is off by`() {
        val sharpA = Pitch.noteOf(Pitch.frequencyOf(69, 440) * 1.005f, 440)
        assertEquals(69, sharpA.note)
        assertEquals(8.63f, sharpA.cents, 0.05f)
        val flatE = Pitch.noteOf(82f, 440)
        assertEquals(40, flatE.note)
        assertTrue(flatE.cents < 0)
    }

    @Test
    fun `no reading is more than half a semitone from its note`() {
        var frequency = 30f
        while (frequency < 2_100f) {
            assertTrue(abs(Pitch.noteOf(frequency, 440).cents) <= 50.001f, "$frequency Hz")
            frequency *= 1.0071f
        }
    }

    @Test
    fun `a preset reads a sound against its nearest string however far it is`() {
        val nearD = Pitch.nearestString(Pitch.frequencyOf(51, 440), InstrumentTuning.GUITAR, 440)
        assertEquals(50, nearD.note)
        assertEquals(100f, nearD.cents, 0.01f)
        val lowE = Pitch.nearestString(70f, InstrumentTuning.GUITAR, 440)
        assertEquals(40, lowE.note)
        assertTrue(lowE.cents < -100)
    }

    @Test
    fun `the target is the nearest semitone without a tuning and the nearest string with one`() {
        val frequency = Pitch.frequencyOf(47, 440)
        assertEquals(47, Pitch.targetOf(frequency, null, 440).note)
        assertEquals(45, Pitch.targetOf(frequency, InstrumentTuning.GUITAR, 440).note)
    }
}
