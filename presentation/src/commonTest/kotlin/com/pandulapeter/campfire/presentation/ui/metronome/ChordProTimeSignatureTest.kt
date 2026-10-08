/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordProTimeSignatureTest {

    @Test
    fun everySignatureChordProReadsIsOneTheClickCanCount() {
        val texts = (0..20).flatMap { beats -> (0..20).map { unit -> "$beats/$unit" } } + listOf("C", "C|", "¢")
        for (text in texts) {
            val (beats, unit) = ChordProTime.parse(text) ?: continue
            assertEquals(TimeSignature(beats, unit), timeSignatureOf(text), text)
        }
    }

    @Test
    fun theMarksOfAScoreAreReadAsTheirFractions() {
        assertEquals(TimeSignature.COMMON_TIME, timeSignatureOf("C"))
        assertEquals(TimeSignature(2, 2), timeSignatureOf("C|"))
    }

    @Test
    fun anythingElseIsNone() {
        assertNull(timeSignatureOf(null))
        assertNull(timeSignatureOf("17/4"))
        assertNull(timeSignatureOf("3/5"))
        assertNull(timeSignatureOf("waltz"))
    }
}
