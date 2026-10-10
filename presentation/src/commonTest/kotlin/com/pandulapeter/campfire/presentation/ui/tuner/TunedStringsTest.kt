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

import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
