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
import com.pandulapeter.campfire.chordpro.ChordProNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongChordsTest {

    private fun names(text: String, notation: ChordNotation = ChordNotation.STANDARD, instrument: ChordInstrument = ChordInstrument.GUITAR, capo: Int = 0) =
        songChordsOf(ChordProNotation.toNotation(ChordProParser.parse(text), notation), notation, instrument, capo)

    @Test
    fun `every chord is listed once, in the order it is first played`() {
        assertEquals(
            listOf("G", "C", "D", "Em"),
            names("[G]Hello [C]there [G]again\n\n{soc}\n[D]la [Em]la [C]la\n{eoc}\n\n{chorus}").map { it.name },
        )
    }

    @Test
    fun `grids, tab chord rows, comments and labels are read too`() {
        assertEquals(
            listOf("Am", "F", "G", "C", "E7", "D"),
            names("{comment: Intro: [Am] [F]}\n{start_of_grid}\n| G . C~E7 . |\n{end_of_grid}\n{start_of_tab}\nD\ne|---0---|\n{end_of_tab}").map { it.name },
        )
    }

    @Test
    fun `annotations, N C and words in brackets are no chords`() {
        assertEquals(listOf("G"), names("[*softly][G]la [N.C.]la [Bridge]la").map { it.name })
    }

    @Test
    fun `a songbook gets no section`() {
        val many = (0 until 12).flatMap { root -> listOf("", "m", "7", "maj7", "sus4").map { listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")[root] + it } }
        assertTrue(many.distinct().size > MAX_SONG_CHORDS)
        assertEquals(emptyList(), names(many.joinToString(" ") { "[$it]la" }))
        assertEquals(MAX_SONG_CHORDS, names(many.take(MAX_SONG_CHORDS).joinToString(" ") { "[$it]la" }).size)
    }

    @Test
    fun `German names are read as German`() {
        val chord = names("[B]la [Bb]la", notation = ChordNotation.GERMAN)
        assertEquals(listOf("H", "B"), chord.map { it.name })
        assertEquals(listOf(11, 10), chord.map { it.chord.root })
    }

    @Test
    fun `the keyboard draws the chord a capo makes sound and names it`() {
        val chords = names("{key: G}\n[G]la [Em]la", instrument = ChordInstrument.KEYBOARD, capo = 2)
        assertEquals(listOf("A", "F#m"), chords.map { it.soundingName })
        assertEquals(ChordProChords.parse("A"), chords.first().chord)
        assertEquals(ChordVoicing.Keys(listOf(9, 13, 16)), chords.first().defaultShape)
        val fretted = names("[G]la", instrument = ChordInstrument.GUITAR, capo = 2).single()
        assertNull(fretted.soundingName)
        assertEquals(ChordProChords.parse("G"), fretted.chord)
    }
}
