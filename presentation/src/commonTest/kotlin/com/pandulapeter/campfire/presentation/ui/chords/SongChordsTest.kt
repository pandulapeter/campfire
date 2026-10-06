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
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongChordsTest {

    private fun names(text: String, notation: ChordNotation = ChordNotation.STANDARD, instrument: ChordInstrument = ChordInstrument.GUITAR, capo: Int = 0) =
        songChordsOf(ChordProParser.parse(text), notation, instrument, capo)

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
    fun `the first build leaves the search out`() {
        val unusual = ChordProChords.parse("Ebmaj9#11/Bb")!!
        assertTrue(ChordVoicings.needsSearch(unusual, ChordInstrument.GUITAR))
        val chords = songChordsOf(ChordProParser.parse("[C]la [Ebmaj9#11/Bb]la"), ChordNotation.STANDARD, ChordInstrument.GUITAR, searchesShapes = false)
        assertFalse(chords[0].isShapePending)
        assertNotNull(chords[0].defaultShape)
        assertTrue(chords[1].isShapePending)
        assertNull(chords[1].defaultShape)
        val searched = chords.withSearchedShapes(ChordInstrument.GUITAR)
        assertFalse(searched[1].isShapePending)
        assertEquals(ChordVoicings.default(unusual, ChordInstrument.GUITAR), searched[1].defaultShape)
        assertFalse(
            songChordsOf(ChordProParser.parse("[Fbmaj9#11/Cb]la"), ChordNotation.STANDARD, ChordInstrument.KEYBOARD, searchesShapes = false).single().isShapePending,
        )
        val defined = songChordsOf(
            ChordProParser.parse("{define: Gbmaj9#11 base-fret 1 frets 2 x 3 3 2 3}\n[Gbmaj9#11]la"),
            ChordNotation.STANDARD,
            ChordInstrument.GUITAR,
            searchesShapes = false,
        ).single()
        assertTrue(defined.definition != null)
        assertFalse(defined.isShapePending)
    }

    @Test
    fun `a capo past the twelfth fret is read as the twelfth`() {
        listOf(12, 15).forEach { capo ->
            val chord = names("{capo: 15}\n[C]la", instrument = ChordInstrument.KEYBOARD, capo = capo).single()
            assertNull(chord.soundingName)
            assertEquals(ChordProChords.parse("C"), chord.chord)
        }
        val moved = names("{capo: 3}\n[C]la", instrument = ChordInstrument.KEYBOARD, capo = 3).single()
        assertEquals(3, moved.chord.root)
        assertEquals("Eb", moved.soundingName)
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
    fun `the chords are named in the reader's notation`() {
        val german = names("[B]la [Bb]la", notation = ChordNotation.GERMAN)
        assertEquals(listOf("H", "B"), german.map { it.name })
        assertEquals(listOf(11, 10), german.map { it.chord.root })
        val latin = names("[Am]la [Bb7]la", notation = ChordNotation.LATIN)
        assertEquals(listOf("Lam", "Sib7"), latin.map { it.name })
        assertEquals(listOf(null, null), latin.map { it.letterName })
        assertEquals(listOf("Am", "Bb7"), latin.map { it.spelling })
    }

    @Test
    fun `a numbered chord is named by its step and its letters`() {
        val chords = names("{key: C}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[C]la [Am]la [G]la", notation = ChordNotation.NASHVILLE)
        assertEquals(listOf("1", "6-", "5"), chords.map { it.name })
        assertEquals(listOf("C", "Am", "G"), chords.map { it.letterName })
        assertEquals(listOf("C", "Am", "G"), chords.map { it.secondaryName })
        assertEquals(ChordProChords.parse("Am"), chords[1].chord)
        assertTrue(chords[2].definition != null)
        assertEquals(listOf("I", "vi", "V"), names("{key: C}\n[C]la [Am]la [G]la", notation = ChordNotation.ROMAN).map { it.name })
    }

    @Test
    fun `a song with no key is listed in letters under a numbering`() {
        val chords = names("[C]la [Am]la", notation = ChordNotation.NASHVILLE)
        assertEquals(listOf("C", "Am"), chords.map { it.name })
        assertEquals(listOf(null, null), chords.map { it.letterName })
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
        assertEquals(listOf("La", "Fa#m"), names("{key: G}\n[G]la [Em]la", ChordNotation.LATIN, ChordInstrument.KEYBOARD, capo = 2).map { it.soundingName })
        val numbered = names("{key: G}\n[G]la", ChordNotation.NASHVILLE, ChordInstrument.KEYBOARD, capo = 2).single()
        assertEquals("1", numbered.name)
        assertEquals("A", numbered.secondaryName)
        assertEquals("Bb", names("{key: D}\n[A]la", instrument = ChordInstrument.KEYBOARD, capo = 1).single().spelling)
        assertEquals(listOf("Bb", "D", "F"), ChordProChords.spelledNoteNames("Bb"))
    }
}
