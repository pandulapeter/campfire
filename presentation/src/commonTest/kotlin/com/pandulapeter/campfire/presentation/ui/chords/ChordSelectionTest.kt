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

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTransposer
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

    private fun chordsOf(text: String, transposition: Int = 0, instrument: ChordInstrument = ChordInstrument.GUITAR, capo: Int = 0) = songChordsOf(
        song = ChordProTransposer.transpose(ChordProParser.parse(text), transposition),
        notation = ChordNotation.STANDARD,
        instrument = instrument,
        capo = capo,
    )

    @Test
    fun `the song's own shape wins over the player's`() {
        val chord = chordsOf("{define: G frets 3 x 0 0 3 3}\n[G]la").single()
        val stored = mapOf(chord.chord.id to "3 2 0 0 0 3")
        assertEquals(
            SelectedShape(ChordVoicing.Fretted(listOf(3, null, 0, 0, 3, 3)), SelectedShape.Source.DEFINED),
            selectShape(chord, ChordInstrument.GUITAR, stored),
        )
    }

    @Test
    fun `a definition for another instrument is not used on the page`() {
        val chord = chordsOf("{define: G frets 0 2 3 2}\n[G]la").single()
        assertEquals(SelectedShape.Source.DEFAULT, selectShape(chord, ChordInstrument.GUITAR, emptyMap()).source)
    }

    @Test
    fun `a moved definition is drawn only while a hand can hold it`() {
        val moved = chordsOf("{define: E frets 0 2 2 1 0 0}\n[E]la", transposition = 1).single()
        assertEquals("F", moved.name)
        assertEquals(SelectedShape(ChordVoicing.Fretted(listOf(1, 3, 3, 2, 1, 1)), SelectedShape.Source.DEFINED, movedBy = 1), selectShape(moved, ChordInstrument.GUITAR, emptyMap()))
        // Five fingers once the open strings are stopped, in either octave.
        val wide = chordsOf("{define: C frets 0 3 2 0 1 3}\n[C]la", transposition = 2).single()
        assertEquals(SelectedShape.Source.DEFAULT, selectShape(wide, ChordInstrument.GUITAR, emptyMap()).source)
        // As the file writes it, it is drawn whatever it asks of a hand.
        val written = chordsOf("{define: C frets 1 3 2 4 1 5}\n[C]la").single()
        assertEquals(SelectedShape.Source.DEFINED, selectShape(written, ChordInstrument.GUITAR, emptyMap()).source)
    }

    @Test
    fun `a keyboard definition is played as it sounds above the capo`() {
        val chord = chordsOf("{define: G keys 0 4 7 12}\n[G]la", instrument = ChordInstrument.KEYBOARD, capo = 2).single()
        assertEquals(ChordVoicing.Keys(listOf(9, 13, 16, 21)), selectShape(chord, ChordInstrument.KEYBOARD, emptyMap()).shape)
    }
}
