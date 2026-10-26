/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import com.pandulapeter.campfire.presentation.ui.chords.libraryKeys
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ChordLibraryTest {

    private val keys = libraryKeys(UserPreferences.Accidentals.ORIGINAL)

    private fun results(
        query: String = "",
        keyIndex: Int = 0,
        notation: ChordNotation = ChordNotation.STANDARD,
        tuning: ChordTuning = ChordTuning.GUITAR,
    ) = assertNotNull(chordLibraryResults(keys, keyIndex, query, notation, tuning))

    @Test
    fun `every chord a key lists is a chord of its own`() {
        keys.forEach { key ->
            val chords = CHORD_LIBRARY_QUALITIES.map { quality -> assertNotNull(ChordProChords.parse(key + quality), key + quality) }
            assertEquals(chords.size, chords.toSet().size, key)
            assertTrue(chords.all { it.root == ChordProChords.parse(key)!!.root }, key)
        }
    }

    @Test
    fun `a key lists its chords in order, each with a shape`() {
        val chords = assertIs<ChordLibraryResults.Chords>(results(keyIndex = 9)).chords
        assertEquals(listOf("A", "Am", "A7"), chords.take(3).map { it.name })
        assertTrue(chords.all { it.defaultShape != null })
        assertEquals("x 0 2 2 2 0", chords.first().defaultShape?.let(ChordVoicings::write))
    }

    @Test
    fun `under a capo a key keeps its names and each chord is the shape fingered above the capo`() {
        val chords = assertIs<ChordLibraryResults.Chords>(assertNotNull(chordLibraryResults(keys, 9, "", ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2))).chords
        assertEquals(listOf("A", "Am", "A7"), chords.take(3).map { it.name })
        assertEquals(ChordProChords.parse("A"), chords.first().chord)
        assertEquals(ChordProChords.parse("G"), chords.first().shapeChord)
        assertEquals(2, chords.first().capo)
        assertEquals(ChordVoicings.default(ChordProChords.parse("G")!!, ChordTuning.GUITAR), chords.first().defaultShape)
        // A searched chord is moved the same way: a D/F# at the second fret is a C/E shape.
        val slash = assertIs<ChordLibraryResults.Chords>(assertNotNull(chordLibraryResults(keys, 0, "D/F#", ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2)))
        assertEquals("D/F♯", slash.searched?.name)
        assertEquals(ChordProChords.parse("C/E"), slash.searched?.shapeChord)
    }

    @Test
    fun `the chords are named in the reader's notation`() {
        val chords = assertIs<ChordLibraryResults.Chords>(results(keyIndex = 11, notation = ChordNotation.GERMAN)).chords
        assertEquals("H", chords.first().name)
        assertEquals("B", chords.first().spelling)
    }

    @Test
    fun `a search narrows the key's chords to the names that hold it, ignoring case`() {
        assertEquals(listOf("Csus2", "Csus4", "C7sus4"), assertIs<ChordLibraryResults.Chords>(results(query = " sus ")).chords.map { it.name })
        assertEquals(listOf("Cmaj7", "Cmaj9", "Cmaj13", "Cmaj7♭5", "Cmaj7♯5"), assertIs<ChordLibraryResults.Chords>(results(query = "cMAJ")).chords.map { it.name })
    }

    @Test
    fun `a found chord keeps the name the key row gives it, whatever case was typed`() {
        assertTrue("Lam7" in assertIs<ChordLibraryResults.Chords>(results(query = "LAM7", keyIndex = 9, notation = ChordNotation.LATIN)).chords.map { it.name })
        assertEquals("F♯m7♭5", assertIs<ChordLibraryResults.Chords>(results(query = "f#m7b5", keyIndex = 6)).chords.single().name)
    }

    @Test
    fun `a search that names a chord the key does not list finds it apart from the key's, whatever key is picked`() {
        val slash = assertIs<ChordLibraryResults.Chords>(results(query = "d/f#"))
        assertEquals("D/F♯", slash.searched?.name)
        assertEquals("2 x 0 2 3 2", slash.searched?.defaultShape?.let(ChordVoicings::write))
        assertEquals(emptyList(), slash.chords)
        assertEquals("Emaj7", assertIs<ChordLibraryResults.Chords>(results(query = "Emaj7")).searched?.name)
        val d = assertIs<ChordLibraryResults.Chords>(results(query = "d"))
        assertEquals("D", d.searched?.name)
        assertEquals(listOf("Cadd9", "Cmadd9"), d.chords.take(2).map { it.name })
    }

    @Test
    fun `a searched chord is read and named in the reader's notation`() {
        val chord = assertNotNull(assertIs<ChordLibraryResults.Chords>(results(query = "B/D", notation = ChordNotation.GERMAN)).searched)
        assertEquals("B/D", chord.name)
        assertEquals("Bb/D", chord.spelling)
    }

    @Test
    fun `a searched chord the key lists is drawn once, among the key's`() {
        val results = assertIs<ChordLibraryResults.Chords>(results(query = "Cm7"))
        assertNull(results.searched)
        assertEquals(1, results.chords.count { it.name == "Cm7" })
    }

    @Test
    fun `a search that names no chord and is in no listed name finds nothing`() {
        assertEquals(ChordLibraryResults.NoChord("xyz"), results(query = "xyz"))
    }

    @Test
    fun `without the search, a chord only the search finds leaves the answer to it`() {
        assertNull(chordLibraryResults(keys, 1, "", ChordNotation.STANDARD, ChordTuning.MANDOLIN_AEAE, searchesShapes = false))
        assertNotNull(chordLibraryResults(keys, 7, "sus2", ChordNotation.STANDARD, ChordTuning.GUITAR, searchesShapes = false))
    }

    @Test
    fun `a chord's shape belongs to the tuning it was found for`() {
        val g = assertIs<ChordLibraryResults.Chords>(results(keyIndex = 7)).chords.first()
        val dropD = ChordVoicings.all(g.chord, ChordTuning.GUITAR_DROP_D).map(ChordVoicings::write)
        assertTrue(g.defaultShape?.let(ChordVoicings::write) !in dropD)
    }

    @Test
    fun `a return opens on the results held for the same inputs without asking the tables`() {
        val inputs = ChordLibraryInputs(keys, 2, "", ChordNotation.STANDARD, ChordTuning.GUITAR_DADGAD)
        val held = ChordLibraryShown(inputs, ChordLibraryResults.NoChord("held"))
        assertEquals(held, initialChordLibraryShown(held, inputs.copy()) { error("The tables are not asked.") })
    }

    @Test
    fun `other inputs open on what the tables answer`() {
        val held = ChordLibraryShown(ChordLibraryInputs(keys, 2, "", ChordNotation.STANDARD, ChordTuning.GUITAR), ChordLibraryResults.NoChord("held"))
        val inputs = held.inputs.copy(keyIndex = 3)
        val answer = ChordLibraryResults.NoChord("tables")
        assertEquals(ChordLibraryShown(inputs, answer), initialChordLibraryShown(held, inputs) { answer })
    }

    @Test
    fun `another key the tables cannot answer keeps the held results of the same tuning`() {
        val held = ChordLibraryShown(ChordLibraryInputs(keys, 2, "", ChordNotation.STANDARD, ChordTuning.GUITAR_DADGAD), ChordLibraryResults.NoChord("held"))
        assertEquals(held, initialChordLibraryShown(held, held.inputs.copy(keyIndex = 7)) { null })
    }

    @Test
    fun `held results of another tuning are dropped where the tables cannot answer`() {
        val held = ChordLibraryShown(ChordLibraryInputs(keys, 2, "", ChordNotation.STANDARD, ChordTuning.GUITAR), ChordLibraryResults.NoChord("held"))
        assertNull(initialChordLibraryShown(held, held.inputs.copy(tuning = ChordTuning.GUITAR_DADGAD)) { null })
        assertNull(initialChordLibraryShown(null, held.inputs) { null })
    }
}
