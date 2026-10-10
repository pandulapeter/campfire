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
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchTrackerTest {

    private val chromatic = TunerConfig(referencePitch = 440, tuning = null)
    private var time = 0L

    @Test
    fun `the attack of a pluck is skipped`() {
        val tracker = PitchTracker()
        // The first window after silence is the onset; the next ones still fall within the attack.
        assertNull(tracker.step(silence(), next(), chromatic).reading)
        assertNull(tracker.step(heard(frequencyOf(45), level = 0.2f), next(), chromatic).reading)
        assertNull(tracker.step(heard(frequencyOf(45, cents = 30f), level = 0.25f), next(), chromatic).reading)
        repeat(PitchTracker.TARGET_HOLD_COUNT) { tracker.step(heard(frequencyOf(45), level = 0.25f), next(), chromatic) }
        val reading = assertNotNull(tracker.step(heard(frequencyOf(45), level = 0.25f), next(), chromatic).reading)
        assertEquals(45, reading.note)
        assertTrue(abs(reading.cents) < 10f, "${reading.cents}")
    }

    @Test
    fun `the note does not flicker at a boundary between two`() {
        val tracker = steadyTracker(note = 57, cents = 45f)
        val notes = (0 until 12).map { index ->
            val cents = if (index % 2 == 0) 52f else 48f
            tracker.step(heard(frequencyOf(57, cents)), next(), chromatic).reading?.note
        }
        assertEquals(1, notes.toSet().size, "$notes")
    }

    @Test
    fun `a new note is taken once it has held`() {
        val tracker = steadyTracker(note = 57)
        val notes = (0 until 8).map { tracker.step(heard(frequencyOf(64)), next(), chromatic).reading?.note }
        assertEquals(57, notes.first())
        assertEquals(64, notes.last())
    }

    @Test
    fun `a new note is never read against the old one`() {
        val tracker = steadyTracker(note = 57)
        val steady = tracker.step(heard(frequencyOf(57)), next(), chromatic).reading
        val readings = (0 until 8).map { tracker.step(heard(frequencyOf(64)), next(), chromatic).reading }
        readings.forEach { reading -> assertTrue(reading == steady || abs(assertNotNull(reading).cents) <= 50f, "$reading") }
        assertEquals(64, readings.last()?.note)
    }

    @Test
    fun `a jump to another string keeps the previous string until the new one holds`() {
        val guitar = TunerConfig(referencePitch = 440, tuning = InstrumentTuning.GUITAR)
        val tracker = PitchTracker()
        tracker.step(silence(), next(), guitar)
        repeat(12) { tracker.step(heard(frequencyOf(45)), next(), guitar) }
        val steady = assertNotNull(tracker.step(heard(frequencyOf(45)), next(), guitar).reading)
        val readings = (0 until 8).map { assertNotNull(tracker.step(heard(frequencyOf(50)), next(), guitar).reading) }
        readings.forEach { reading ->
            val isPrevious = reading.note == steady.note && reading.cents == steady.cents
            assertTrue(isPrevious || (reading.note == 50 && abs(reading.cents) < 10f), "$reading")
        }
        assertEquals(50, readings.last().note)
    }

    @Test
    fun `the last reading is held for a moment after the sound fades and then let go`() {
        val tracker = steadyTracker(note = 57)
        time += PitchTracker.HOLD_MILLIS / 2
        assertEquals(57, tracker.step(quiet(), time, chromatic).reading?.note)
        time += PitchTracker.HOLD_MILLIS
        assertNull(tracker.step(quiet(), time, chromatic).reading)
    }

    @Test
    fun `in tune is said only once it has stayed in tune`() {
        val tracker = steadyTracker(note = 57, cents = 20f)
        val start = time
        var reading = tracker.step(heard(frequencyOf(57, 2f)), next(), chromatic).reading
        assertFalse(assertNotNull(reading).isInTune)
        while (time - start < PitchTracker.IN_TUNE_DELAY_MILLIS) reading = tracker.step(heard(frequencyOf(57, 2f)), next(), chromatic).reading
        assertFalse(assertNotNull(reading).isInTune)
        while (time - start < PitchTracker.IN_TUNE_DELAY_MILLIS * 3) reading = tracker.step(heard(frequencyOf(57, 2f)), next(), chromatic).reading
        assertTrue(assertNotNull(reading).isInTune)
        repeat(10) { reading = tracker.step(heard(frequencyOf(57, 20f)), next(), chromatic).reading }
        assertFalse(assertNotNull(reading).isInTune)
    }

    @Test
    fun `a preset reads against its nearest string`() {
        val guitar = TunerConfig(referencePitch = 440, tuning = InstrumentTuning.GUITAR)
        val tracker = PitchTracker()
        tracker.step(silence(), next(), guitar)
        var reading = tracker.step(heard(frequencyOf(46)), next(), guitar).reading
        repeat(8) { reading = tracker.step(heard(frequencyOf(46)), next(), guitar).reading }
        assertEquals(45, assertNotNull(reading).note)
        assertEquals(100f, reading.cents, 2f)
    }

    @Test
    fun `digital silence held for two seconds is silent and anything else is not`() {
        val tracker = PitchTracker()
        assertFalse(tracker.step(silence(), next(), chromatic).isSilent)
        time += PitchTracker.SILENT_MILLIS
        assertTrue(tracker.step(silence(), time, chromatic).isSilent)
        assertFalse(tracker.step(quiet(), next(), chromatic).isSilent)
    }

    @Test
    fun `a ringing string read an octave away now and then keeps its note`() {
        val tracker = steadyTracker(note = 45)
        val notes = (0 until 12).map { index ->
            val note = if (index % 3 == 2) 45 else 33
            tracker.step(heard(frequencyOf(note), level = 0.15f), next(), chromatic).reading?.note
        }
        assertEquals(setOf(45), notes.toSet(), "$notes")
    }

    @Test
    fun `an octave struck anew is taken`() {
        val tracker = steadyTracker(note = 45, level = 0.05f)
        val notes = (0 until 12).map { tracker.step(heard(frequencyOf(57), level = 0.2f), next(), chromatic).reading?.note }
        assertEquals(57, notes.last(), "$notes")
    }

    @Test
    fun `an octave too soft to be heard as struck is taken once the held note is let go`() {
        val tracker = steadyTracker(note = 45)
        val notes = (0 until 40).map { tracker.step(heard(frequencyOf(57), level = 0.2f), next(), chromatic).reading?.note }
        assertEquals(57, notes.last(), "$notes")
    }

    private fun steadyTracker(note: Int, cents: Float = 0f, level: Float = 0.2f) = PitchTracker().also { tracker ->
        tracker.step(silence(), next(), chromatic)
        repeat(12) { tracker.step(heard(frequencyOf(note, cents), level), next(), chromatic) }
    }

    private fun next() = (time + STEP_MILLIS).also { time = it }

    private fun frequencyOf(note: Int, cents: Float = 0f) = Pitch.frequencyOf(note, 440) * 2f.pow(cents / 1_200)

    private fun heard(frequency: Float, level: Float = 0.2f) = PitchEstimate(frequency = frequency, clarity = 0.95f, level = level)

    private fun quiet() = PitchEstimate(frequency = null, clarity = 0.1f, level = 0.002f)

    private fun silence() = PitchEstimate(frequency = null, clarity = 0f, level = 0f)

    private companion object {
        const val STEP_MILLIS = 33L
    }
}
