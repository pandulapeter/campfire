/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.StringCourses
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunedStringsTest {

    @Test
    fun `a reading in tune adds its note and one off does not`() {
        assertEquals(setOf(40, 45), tunedNotesAfter(setOf(40), TunerListening.Hearing(TunerReading(note = 45, cents = 2f, frequency = 110f, isInTune = true))))
        assertEquals(setOf(40), tunedNotesAfter(setOf(40), TunerListening.Hearing(TunerReading(note = 45, cents = 12f, frequency = 111f, isInTune = false))))
        assertEquals(setOf(40), tunedNotesAfter(setOf(40), TunerListening.Hearing()))
    }

    @Test
    fun `the notes are kept while the microphone opens and forgotten once it closes`() {
        assertEquals(setOf(40), tunedNotesAfter(setOf(40), TunerListening.Starting))
        assertEquals(emptySet(), tunedNotesAfter(setOf(40), TunerListening.Stopped()))
        assertEquals(emptySet(), tunedNotesAfter(setOf(40), TunerListening.Stopped(TunerStopReason.MICROPHONE_DISCONNECTED)))
    }

    @Test
    fun `only a string the tuner listens for is a string`() {
        val mandolin = TunerConfig(440, InstrumentTuning.MANDOLIN, StringCourses.UNISON_PAIRS)
        assertTrue(isStringOf(mandolin, 55))
        assertTrue(isStringOf(mandolin, 76))
        assertFalse(isStringOf(mandolin, 40))
    }

    @Test
    fun `the second string of a course is a string`() {
        val twelveString = TunerConfig(440, InstrumentTuning.GUITAR, StringCourses.TWELVE_STRING)
        assertTrue(isStringOf(twelveString, 52))
        assertFalse(isStringOf(TunerConfig(440, InstrumentTuning.GUITAR), 52))
    }

    @Test
    fun `nothing is a string in chromatic mode`() {
        assertFalse(isStringOf(TunerConfig(440, null), 40))
        assertFalse(isStringOf(TunerConfig(440, null), 69))
    }
}
