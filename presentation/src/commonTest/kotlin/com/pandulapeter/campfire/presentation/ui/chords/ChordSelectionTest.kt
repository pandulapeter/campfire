/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordSelectionTest {

    private fun songChord(name: String, instrument: ChordInstrument = ChordInstrument.GUITAR) = ChordProChords.parse(name)!!.let {
        SongChord(name = name, chord = it, defaultShape = ChordVoicings.default(it, instrument))
    }

    @Test
    fun `a chord nobody chose a shape for is drawn with the app's own`() {
        assertEquals(
            SelectedShape(ChordVoicing.Fretted(listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1)), SelectedShape.Source.DEFAULT),
            selectShape(songChord("F"), ChordInstrument.GUITAR, emptyMap()),
        )
    }

    @Test
    fun `the player's shape is drawn for every spelling of the chord`() {
        val stored = mapOf(ChordProChords.parse("F")!!.id to "x x 3 2 1 1")
        val selected = selectShape(songChord("F"), ChordInstrument.GUITAR, stored)
        assertEquals(SelectedShape.Source.PLAYER, selected.source)
        assertEquals(listOf(null, null, 3, 2, 1, 1), (selected.shape as ChordVoicing.Fretted).frets)
        assertEquals(listOf(0, 0, 3, 2, 1, 1), selected.shape.fingers, "the tables' fingering comes back with the shape")
        val sharp = mapOf(ChordProChords.parse("C#m7")!!.id to "x 4 2 4 5 x")
        assertEquals(SelectedShape.Source.PLAYER, selectShape(songChord("Dbm7"), ChordInstrument.GUITAR, sharp).source)
    }

    @Test
    fun `a stored shape of another instrument is not drawn`() {
        val stored = mapOf(ChordProChords.parse("C")!!.id to "x 3 2 0 1 0")
        assertEquals(SelectedShape.Source.DEFAULT, selectShape(songChord("C", ChordInstrument.UKULELE), ChordInstrument.UKULELE, stored).source)
    }
}
