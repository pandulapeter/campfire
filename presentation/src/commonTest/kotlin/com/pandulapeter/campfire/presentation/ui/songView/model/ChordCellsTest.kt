/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordCellsTest {

    private val chords = songChordsOf(ChordProParser.parse("[G]la [C]la"), ChordNotation.STANDARD, ChordTuning.GUITAR)

    @Test
    fun `chords collected for another tuning of the instrument give no cells`() {
        assertNull(chordCellsOf(chords, ChordTuning.GUITAR, ChordTuning.GUITAR_DROP_D, emptyMap()))
    }

    @Test
    fun `chords collected for the tuning drawn give its cells`() {
        assertEquals(
            chordCellsOf(chords, ChordTuning.GUITAR, emptyMap()),
            chordCellsOf(chords, ChordTuning.GUITAR, ChordTuning.GUITAR, emptyMap()),
        )
    }

    @Test
    fun `chords collected for another instrument give no cells`() {
        assertNull(chordCellsOf(chords, ChordTuning.GUITAR, ChordTuning.UKULELE, emptyMap()))
    }

    @Test
    fun `chords collected without a tuning give no cells in any tuning`() {
        assertNull(chordCellsOf(chords, null, ChordTuning.GUITAR, emptyMap()))
        assertNull(chordCellsOf(chords, null, ChordTuning.UKULELE, emptyMap()))
    }
}
