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

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ChordLibraryTest {

    private val keys = chordLibraryKeys(UserPreferences.Accidentals.ORIGINAL)

    private fun results(
        query: String = "",
        keyIndex: Int = 0,
        notation: ChordNotation = ChordNotation.STANDARD,
        tuning: ChordTuning = ChordTuning.GUITAR,
    ) = assertNotNull(chordLibraryResults(keys, keyIndex, query, notation, tuning))

    @Test
    fun `every spelling of the keys names the twelve notes from C`() {
        UserPreferences.Accidentals.entries.forEach { accidentals ->
            assertEquals((0 until 12).toList(), chordLibraryKeys(accidentals).map { ChordProChords.parse(it)!!.root })
        }
    }

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
    fun `the chords are named in the reader's notation`() {
        val chords = assertIs<ChordLibraryResults.Chords>(results(keyIndex = 11, notation = ChordNotation.GERMAN)).chords
        assertEquals("H", chords.first().name)
        assertEquals("B", chords.first().spelling)
    }

    @Test
    fun `a search naming a chord finds that chord alone, whatever the key`() {
        val chords = assertIs<ChordLibraryResults.Chords>(results(query = " F#m7b5 ")).chords
        assertEquals(listOf("F#m7b5"), chords.map { it.name })
        assertEquals(ChordProChords.parse("Gbm7b5"), chords.single().chord)
        val latin = assertIs<ChordLibraryResults.Chords>(results(query = "Lam", notation = ChordNotation.LATIN)).chords.single()
        assertEquals(ChordProChords.parse("Am"), latin.chord)
    }

    @Test
    fun `a chord found by a search is named with its root in capitals, or in title case in Latin`() {
        val standard = assertIs<ChordLibraryResults.Chords>(results(query = "c7")).chords.single()
        assertEquals("Cm7", standard.name)
        assertEquals("Cm7", standard.spelling)
        assertEquals("Lam7", assertIs<ChordLibraryResults.Chords>(results(query = "LAm7", notation = ChordNotation.LATIN)).chords.single().name)
    }

    @Test
    fun `any other search narrows the key's chords to the names that hold it`() {
        val chords = assertIs<ChordLibraryResults.Chords>(results(query = "sus")).chords
        assertEquals(listOf("Csus2", "Csus4", "C7sus4"), chords.map { it.name })
    }

    @Test
    fun `a search that is no chord says so`() {
        assertEquals(ChordLibraryResults.NoChord("xyz"), results(query = "xyz"))
    }

    @Test
    fun `a chord with no shape on the instrument says so`() {
        val crowded = "C(b9,9,#9,11,#11,b13,13,#13,maj7)"
        assertEquals(ChordLibraryResults.NoShape(crowded), results(query = crowded, tuning = ChordTuning.UKULELE))
    }

    @Test
    fun `without the search, a chord only the search finds leaves the answer to it`() {
        assertNull(chordLibraryResults(keys, 0, "Dbmaj9#11/Ab", ChordNotation.STANDARD, ChordTuning.MANDOLIN_AEAE, searchesShapes = false))
        assertNotNull(chordLibraryResults(keys, 0, "G", ChordNotation.STANDARD, ChordTuning.GUITAR, searchesShapes = false))
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
