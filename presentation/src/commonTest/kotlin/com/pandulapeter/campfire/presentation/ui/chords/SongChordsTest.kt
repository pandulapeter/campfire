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

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongChordsTest {

    private fun names(text: String, notation: ChordNotation = ChordNotation.STANDARD, tuning: ChordTuning = ChordTuning.GUITAR) =
        songChordsOf(ChordProParser.parse(text), notation, tuning)

    @Test
    fun `under a capo a chord is named as it sounds and fingered as the shape above the capo`() {
        val chord = songChordsOf(ChordProParser.parse("[A]la"), ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2).single()
        assertEquals("A", chord.name)
        assertEquals(ChordProChords.parse("A"), chord.chord)
        assertEquals(ChordProChords.parse("G"), chord.shapeChord)
        assertEquals(2, chord.capo)
        assertEquals(ChordVoicings.default(ChordProChords.parse("G")!!, ChordTuning.GUITAR), chord.defaultShape)
    }

    @Test
    fun `a file's own shape comes back to where it was written under the capo it names`() {
        val text = "{capo: 2}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[G]la"
        val sounding = ChordProTransposer.transpose(ChordProParser.parse(text), 2)
        val chord = songChordsOf(sounding, ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2).single()
        assertEquals("A", chord.name)
        assertEquals(listOf<Int?>(3, 2, 0, 0, 0, 3), (chord.definition?.voicing as ChordVoicing.Fretted).frets)
        assertEquals(0, chord.definition.movedBy)
    }

    @Test
    fun `a song's own shape is kept under any capo, moved by the reader's transposition alone`() {
        val text = "{capo: 2}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[G]la"
        // The page hands over the song moved by the reader's transposition and the capo played, see `soundingTransposition`.
        fun chordOf(transposition: Int, capo: Int, song: String = text, tuning: ChordTuning = ChordTuning.GUITAR) =
            songChordsOf(ChordProTransposer.transpose(ChordProParser.parse(song), transposition + capo), ChordNotation.STANDARD, tuning, capo = capo).single()
        fun frets(chord: SongChord) = (chord.definition?.voicing as ChordVoicing.Fretted).frets

        val raised = chordOf(transposition = 0, capo = 3)
        assertEquals(ChordProChords.parse("Bb"), raised.chord)
        assertEquals(listOf<Int?>(3, 2, 0, 0, 0, 3), frets(raised))
        assertEquals(0, raised.definition?.movedBy)

        val takenOff = chordOf(transposition = 0, capo = 0)
        assertEquals(ChordProChords.parse("G"), takenOff.chord)
        assertEquals(listOf<Int?>(3, 2, 0, 0, 0, 3), frets(takenOff))

        val transposed = chordOf(transposition = 2, capo = 2)
        assertEquals(ChordProChords.parse("B"), transposed.chord)
        assertEquals(2, transposed.definition?.movedBy)

        val twelfth = chordOf(transposition = 0, capo = 12, song = "{define-ukulele: C frets 0 0 0 3}\n[C]la", tuning = ChordTuning.UKULELE)
        assertEquals(listOf<Int?>(0, 0, 0, 3), frets(twelfth))
        assertEquals(0, twelfth.definition?.movedBy)
    }

    @Test
    fun `a banjo definition is drawn with its drone muted where its note is not the chord's, written so or transposed into it`() {
        fun frets(chord: SongChord) = (chord.definition?.voicing as ChordVoicing.Fretted).frets
        val written = songChordsOf(ChordProParser.parse("{define: D frets 0 4 2 3 4}\n[D]la"), ChordNotation.STANDARD, ChordTuning.BANJO)
        assertEquals(listOf<Int?>(null, 4, 2, 3, 4), frets(written.single()))
        val transposed = ChordProTransposer.transpose(ChordProParser.parse("{define: C frets 0 2 0 1 2}\n[C]la"), 2)
        assertEquals(listOf<Int?>(null, 4, 2, 3, 4), frets(songChordsOf(transposed, ChordNotation.STANDARD, ChordTuning.BANJO).single()))
    }

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
        // The shapes worked out are cached for the whole process and only :chordpro's own tests can reset that cache, so
        // these chords are ones no other test of this module asks for.
        val unusual = ChordProChords.parse("Ebmaj9#11/Bb")!!
        assertTrue(ChordVoicings.needsSearch(unusual, ChordTuning.GUITAR))
        val chords = songChordsOf(ChordProParser.parse("[C]la [Ebmaj9#11/Bb]la"), ChordNotation.STANDARD, ChordTuning.GUITAR, searchesShapes = false)
        assertFalse(chords[0].isShapePending)
        assertNotNull(chords[0].defaultShape)
        assertTrue(chords[1].isShapePending)
        assertNull(chords[1].defaultShape)
        val searched = chords.withSearchedShapes(ChordTuning.GUITAR)
        assertFalse(searched[1].isShapePending)
        assertEquals(ChordVoicings.default(unusual, ChordTuning.GUITAR), searched[1].defaultShape)
        assertFalse(
            songChordsOf(ChordProParser.parse("[Fbmaj9#11/Cb]la"), ChordNotation.STANDARD, ChordTuning.KEYBOARD, searchesShapes = false).single().isShapePending,
        )
        val defined = songChordsOf(
            ChordProParser.parse("{define: Gbmaj9#11 base-fret 1 frets 2 x 3 3 2 3}\n[Gbmaj9#11]la"),
            ChordNotation.STANDARD,
            ChordTuning.GUITAR,
            searchesShapes = false,
        ).single()
        assertTrue(defined.definition != null)
        assertFalse(defined.isShapePending)
    }

    @Test
    fun `a chord written two ways is listed once`() {
        val lines = "[G]la [(G)]la [a]la [Am]la [A#]la [Bb]la"
        // The parser has already spelled the lowercase minor out.
        assertEquals(listOf("G", "Am", "A♯"), names(lines).map { it.name })
        val bb = "{define: Bb base-fret 1 frets x 1 3 3 3 1}"
        val aSharp = "{define: A# base-fret 1 frets x 1 3 3 3 1}"
        assertTrue(names("$bb\n$lines").single { it.name == "A♯" }.definition != null)
        assertEquals(3, names("$bb\n$lines").size)
        assertEquals(3, names("$aSharp\n$bb\n$lines").size)
        val different = names("$aSharp\n{define: Bb base-fret 6 frets 1 3 3 2 1 1}\n$lines")
        assertEquals(listOf("G", "Am", "A♯", "B♭"), different.map { it.name })
        assertEquals(different[2].chord, different[3].chord)
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
        assertEquals(listOf("Lam", "Si♭7"), latin.map { it.name })
        assertEquals(listOf(null, null), latin.map { it.letterName })
        assertEquals(listOf("Am", "Bb7"), latin.map { it.spelling })
    }

    @Test
    fun `a numbered chord is named by its step and its letters`() {
        val chords = names("{key: C}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[C]la [Am]la [G]la", notation = ChordNotation.NASHVILLE)
        assertEquals(listOf("1", "6-", "5"), chords.map { it.name })
        assertEquals(listOf("C", "Am", "G"), chords.map { it.letterName })
        assertEquals(listOf("C", "Am", "G"), chords.map { it.letterName })
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
    fun `a song with no key is listed by the steps of the key its chords suggest under a numbering`() {
        val chords = songChordsOf(ChordProParser.parse("[C]la [Am]la"), ChordNotation.NASHVILLE, ChordTuning.GUITAR, inferredKey = "C")
        assertEquals(listOf("1", "6-"), chords.map { it.name })
        assertEquals(listOf("C", "Am"), chords.map { it.letterName })
        assertTrue(hasSongChords(ChordProParser.parse("[C]la [Am]la"), ChordNotation.NASHVILLE, inferredKey = "C"))
    }

    @Test
    fun `named as shapes, a capoed chord keeps its shape's name and everything else of the chord that sounds`() {
        // The reporter's song: written in Em at capo 2, so it sounds in F#m.
        val text = "{key: Em}\n{capo: 2}\n[Em]la [C]la [D]la [Bm]la"
        val sounding = ChordProTransposer.transpose(ChordProParser.parse(text), 2)
        val named = ChordProParser.parse(text)
        val chords = songChordsOf(sounding, ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2).namedAsShapes(named, ChordNotation.STANDARD, nameCapo = 2)
        assertEquals(listOf("Em", "C", "D", "Bm"), chords.map { it.name })
        assertEquals(listOf("F#m", "D", "E", "C#m").map(ChordProChords::parse), chords.map { it.chord })
        assertEquals(listOf("Em", "C", "D", "Bm").map(ChordProChords::parse), chords.map { it.shapeChord })
        assertEquals(listOf(2, 2, 2, 2), chords.map { it.nameCapo })
        val german = songChordsOf(sounding, ChordNotation.GERMAN, ChordTuning.GUITAR, capo = 2).namedAsShapes(named, ChordNotation.GERMAN, nameCapo = 2)
        assertEquals("Hm", german.last().name)
    }

    @Test
    fun `named as shapes under a numbering, a chord keeps its step and spells its shape`() {
        val text = "{key: Em}\n[Em]la [C]la"
        val chords = songChordsOf(ChordProTransposer.transpose(ChordProParser.parse(text), 2), ChordNotation.NASHVILLE, ChordTuning.GUITAR, capo = 2)
            .namedAsShapes(ChordProParser.parse(text), ChordNotation.NASHVILLE, nameCapo = 2)
        assertEquals(listOf("1-", "♭6"), chords.map { it.name })
        assertEquals(listOf("Em", "C"), chords.map { it.letterName })
    }

    @Test
    fun `named as what sounds, nothing is renamed`() {
        val sounding = songChordsOf(ChordProParser.parse("[A]la"), ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2)
        assertEquals(sounding, sounding.namedAsShapes(ChordProParser.parse("[G]la"), ChordNotation.STANDARD, nameCapo = 0))
    }

    @Test
    fun `a key named for the shapes is the key moved down by the capo`() {
        assertEquals("Em", "F#m".movedDownBy(2))
        assertEquals("F#m", "F#m".movedDownBy(0))
    }
}
