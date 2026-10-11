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

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals

class TuningNameTest {

    @Test
    fun `a tuning's note is named in the reader's notation`() {
        assertEquals("B", noteOf(InstrumentTuning.GUITAR_DROP_B, ChordNotation.STANDARD))
        assertEquals("H", noteOf(InstrumentTuning.GUITAR_DROP_B, ChordNotation.GERMAN))
        assertEquals("Si", noteOf(InstrumentTuning.GUITAR_DROP_B, ChordNotation.LATIN))
        assertEquals("B", noteOf(InstrumentTuning.GUITAR_DROP_B, ChordNotation.NASHVILLE))
        assertEquals("C#", noteOf(InstrumentTuning.GUITAR_DROP_C_SHARP, ChordNotation.STANDARD))
        assertEquals("Sol", noteOf(InstrumentTuning.BANJO, ChordNotation.LATIN))
    }

    @Test
    fun `a tuning is named after the note it is known by`() {
        assertEquals(TuningName.Noted(TuningName.Noted.Kind.DROP, 38), tuningNameOf(InstrumentTuning.GUITAR_DROP_D))
        assertEquals(TuningName.Noted(TuningName.Noted.Kind.DROP, 21), tuningNameOf(InstrumentTuning.BASS_FIVE_STRING_DROP_A))
        assertEquals(TuningName.Noted(TuningName.Noted.Kind.DOUBLE_DROP, 38), tuningNameOf(InstrumentTuning.GUITAR_DOUBLE_DROP_D))
        assertEquals(TuningName.Noted(TuningName.Noted.Kind.HIGH, 48), tuningNameOf(InstrumentTuning.BASS_FIVE_STRING_HIGH_C))
        assertEquals(TuningName.Noted(TuningName.Noted.Kind.LOW, 55), tuningNameOf(InstrumentTuning.UKULELE_LOW_G))
        listOf(
            InstrumentTuning.GUITAR_OPEN_G to "G",
            InstrumentTuning.GUITAR_OPEN_D to "D",
            InstrumentTuning.GUITAR_OPEN_E to "E",
            InstrumentTuning.GUITAR_OPEN_A to "A",
            InstrumentTuning.GUITAR_OPEN_C to "C",
            InstrumentTuning.BANJO_OPEN_D to "D",
            InstrumentTuning.BANJO_DOUBLE_C to "C",
            InstrumentTuning.BANJO_C to "C",
            InstrumentTuning.BANJO_G_MODAL to "G",
            InstrumentTuning.UKULELE_D to "D",
        ).forEach { (tuning, note) -> assertEquals(note, noteOf(tuning, ChordNotation.STANDARD), tuning.id) }
    }

    @Test
    fun `a spelled tuning runs letters together and keeps syllables apart`() {
        val dadgad = tuningNameOf(InstrumentTuning.GUITAR_DADGAD) as TuningName.Spelled
        assertEquals("DADGAD", spelledTuning(dadgad.strings, ChordNotation.STANDARD))
        assertEquals("DADGAD", spelledTuning(dadgad.strings, ChordNotation.ROMAN))
        assertEquals("Re La Re Sol La Re", spelledTuning(dadgad.strings, ChordNotation.LATIN))
        listOf(
            InstrumentTuning.VIOLIN_AEAE to "AEAE",
            InstrumentTuning.VIOLIN_ADAE to "ADAE",
            InstrumentTuning.VIOLIN_GDGD to "GDGD",
            InstrumentTuning.MANDOLIN_AEAE to "AEAE",
            InstrumentTuning.MANDOLIN_GDGD to "GDGD",
        ).forEach { (tuning, name) ->
            assertEquals(name, spelledTuning((tuningNameOf(tuning) as TuningName.Spelled).strings, ChordNotation.STANDARD))
        }
    }

    private fun noteOf(tuning: InstrumentTuning, notation: ChordNotation) = noteName((tuningNameOf(tuning) as TuningName.Noted).note, notation)
}
