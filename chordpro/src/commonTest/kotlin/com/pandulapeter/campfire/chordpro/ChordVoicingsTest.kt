/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordVoicingsTest {

    @BeforeTest
    fun resetCache() = ChordVoicings.resetCache()

    @Test
    fun `the forty chords every songbook uses are shown the way every chart shows them`() {
        mapOf(
            "C" to "x 3 2 0 1 0", "D" to "x x 0 2 3 2", "E" to "0 2 2 1 0 0", "F" to "1 3 3 2 1 1", "G" to "3 2 0 0 0 3",
            "A" to "x 0 2 2 2 0", "B" to "x 2 4 4 4 2", "Am" to "x 0 2 2 1 0", "Bm" to "x 2 4 4 3 2", "Cm" to "x 3 5 5 4 3",
            "Dm" to "x x 0 2 3 1", "Em" to "0 2 2 0 0 0", "Fm" to "1 3 3 1 1 1", "Gm" to "3 5 5 3 3 3", "C7" to "x 3 2 3 1 0",
            "D7" to "x x 0 2 1 2", "E7" to "0 2 0 1 0 0", "G7" to "3 2 0 0 0 1", "A7" to "x 0 2 0 2 0", "B7" to "x 2 1 2 0 2",
            "Am7" to "x 0 2 0 1 0", "Dm7" to "x x 0 2 1 1", "Em7" to "0 2 0 0 0 0", "Cmaj7" to "x 3 2 0 0 0",
            "Fmaj7" to "x x 3 2 1 0", "Gmaj7" to "3 2 0 0 0 2", "Dsus4" to "x x 0 2 3 3", "Asus4" to "x 0 2 2 3 0",
            "Dsus2" to "x x 0 2 3 0", "Asus2" to "x 0 2 2 0 0", "Cadd9" to "x 3 2 0 3 0", "F#m" to "2 4 4 2 2 2",
            "C#m" to "x 4 6 6 5 4", "Bb" to "x 1 3 3 3 1", "Eb" to "x x 1 3 4 3", "Ab" to "4 6 6 5 4 4",
            "Bbm" to "x 1 3 3 2 1", "G#m" to "4 6 6 4 4 4", "F#7" to "2 4 2 3 2 2", "Bm7" to "x 2 0 2 0 2",
            "Esus4" to "0 2 2 2 0 0", "D/F#" to "2 x 0 2 3 2", "G/B" to "x 2 0 0 0 3", "C/G" to "3 3 2 0 1 0",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordInstrument.GUITAR), name) }
        mapOf(
            "C" to "0 0 0 3", "G" to "0 2 3 2", "Am" to "2 0 0 0", "F" to "2 0 1 0", "D" to "2 2 2 0", "Em" to "0 4 3 2",
            "A" to "2 1 0 0", "Dm" to "2 2 1 0", "G7" to "0 2 1 2", "C7" to "0 0 0 1", "Bb" to "3 2 1 1", "E7" to "1 2 0 2",
            "A7" to "0 1 0 0", "Bm" to "4 2 2 2", "D7" to "2 2 2 3", "F#m" to "2 1 2 0", "C#m" to "1 1 0 4", "E" to "1 4 0 2",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordInstrument.UKULELE), name) }
    }

    @Test
    fun `every shape the search finds keeps its rules`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            listOf(ChordInstrument.GUITAR, ChordInstrument.UKULELE).forEach { instrument ->
                val played = if (instrument == ChordInstrument.UKULELE) chord.copy(bass = null) else chord
                val shapes = ChordVoicings.all(chord, instrument)
                assertEquals(shapes.size, shapes.map { (it as ChordVoicing.Fretted).frets }.distinct().size, "$name has no shape twice")
                shapes.forEach { voicing ->
                    val shape = assertIs<ChordVoicing.Fretted>(voicing)
                    val frets = shape.frets
                    val label = "$name on ${instrument.id}: ${ChordVoicings.write(shape)}"
                    assertEquals(instrument.tuning.size, frets.size, label)
                    assertTrue(played.pitchClasses.containsAll(ChordShapeGeometry.pitchClasses(shape, instrument)), label)
                    assertTrue(ChordShapeGeometry.fingerCount(frets) <= 4, label)
                    val sounding = frets.indices.filter { frets[it] != null }
                    // The tables' own shapes, which carry their fingering, may mute a string with a finger that rests on it.
                    if (shape.fingers == null) assertEquals(sounding.size, sounding.last() - sounding.first() + 1, "$label mutes no string between two that sound")
                    val stopped = frets.filterNotNull().filter { it > 0 }
                    if (stopped.isNotEmpty()) assertTrue(stopped.max() - stopped.min() < 4, label)
                    if (instrument == ChordInstrument.GUITAR) {
                        val lowest = sounding.minOf { instrument.tuning[it] + frets[it]!! } % 12
                        assertTrue(lowest == (chord.bass ?: chord.root), "$label sounds its bass or root lowest")
                    }
                }
            }
        }
    }

    @Test
    fun `the search answers the same twice`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordInstrument.entries.forEach { assertEquals(ChordVoicings.all(chord, it), ChordVoicings.all(chord, it), name) }
        }
    }

    @Test
    fun `the default is the first of all the shapes`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordInstrument.entries.forEach {
                assertEquals(ChordVoicings.all(chord, it).firstOrNull(), ChordVoicings.default(chord, it), name)
                assertEquals(ChordVoicings.all(chord, it).firstOrNull(), ChordVoicings.default(chord, it), "$name, remembered")
            }
        }
    }

    @Test
    fun `the keyboard plays the notes from the root up and their inversions`() {
        assertEquals(
            listOf(ChordVoicing.Keys(listOf(0, 4, 7)), ChordVoicing.Keys(listOf(4, 7, 12)), ChordVoicing.Keys(listOf(7, 12, 16))),
            ChordVoicings.all(ChordProChords.parse("C")!!, ChordInstrument.KEYBOARD),
        )
        assertEquals(ChordVoicing.Keys(listOf(9, 12, 16, 19)), ChordVoicings.default(ChordProChords.parse("Am7")!!, ChordInstrument.KEYBOARD))
        assertEquals(ChordVoicing.Keys(listOf(14, 18, 21), bass = 6), ChordVoicings.default(ChordProChords.parse("D/F#")!!, ChordInstrument.KEYBOARD))
        assertEquals(6, (ChordVoicings.default(ChordProChords.parse("C7(b9,#9,#11,b13)")!!, ChordInstrument.KEYBOARD) as ChordVoicing.Keys).notes.size)
        val thirteenth = ChordVoicings.default(ChordProChords.parse("C13#11")!!, ChordInstrument.KEYBOARD) as ChordVoicing.Keys
        assertEquals(5, thirteenth.notes.size, "the fifth goes first")
    }

    @Test
    fun `a stored shape is read back as it was written`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordInstrument.entries.forEach { instrument ->
                ChordVoicings.all(chord, instrument).forEach { shape ->
                    assertEquals(shape, ChordVoicings.read(ChordVoicings.write(shape), instrument, chord), name)
                }
            }
        }
        assertEquals("x 3 2 0 1 0", ChordVoicings.write(ChordVoicing.Fretted(listOf(null, 3, 2, 0, 1, 0))))
        assertEquals("12 16 19 / 4", ChordVoicings.write(ChordVoicing.Keys(listOf(12, 16, 19), bass = 4)))
        assertEquals(ChordVoicing.Fretted(listOf(null, 3, 2, 0, 1, 0)), ChordVoicings.read("x 3 2 0 1 0", ChordInstrument.GUITAR))
        assertEquals(listOf(0, 3, 2, 0, 1, 0), (ChordVoicings.read("x 3 2 0 1 0", ChordInstrument.GUITAR, ChordProChords.parse("C")) as ChordVoicing.Fretted).fingers)
        listOf("0 4 48", "0 4 99999999", "4 7 / -5", "4 7 / 12").forEach { assertNull(ChordVoicings.read(it, ChordInstrument.KEYBOARD), it) }
        listOf("0 4 47", "4 7 / 11").forEach { assertNotNull(ChordVoicings.read(it, ChordInstrument.KEYBOARD), it) }
        listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").forEach { root ->
            listOf("", "m7", "maj9", "13#11", "/E").forEach { quality ->
                val chord = ChordProChords.parse(root + quality)!!
                ChordVoicings.all(chord, ChordInstrument.KEYBOARD).forEach { shape ->
                    assertEquals(shape, ChordVoicings.read(ChordVoicings.write(shape), ChordInstrument.KEYBOARD, chord), root + quality)
                }
            }
        }
    }

    @Test
    fun `a shape of another instrument is not read`() {
        assertNull(ChordVoicings.read("x 3 2 0 1 0", ChordInstrument.UKULELE))
        assertNull(ChordVoicings.read("0 0 0 3", ChordInstrument.GUITAR))
        assertNull(ChordVoicings.read("x 3 two 0 1 0", ChordInstrument.GUITAR))
        assertNull(ChordVoicings.read("", ChordInstrument.KEYBOARD))
        assertNull(ChordVoicings.read("4 7 /", ChordInstrument.KEYBOARD))
    }

    @Test
    fun `a chord with no shape answers no shape rather than a wrong one`() {
        val crowded = ChordProChords.parse("C7(b9, #9, #11, b13)")!!
        assertEquals(emptyList(), ChordVoicings.all(crowded, ChordInstrument.UKULELE))
        assertNull(ChordVoicings.default(crowded, ChordInstrument.UKULELE))
    }

    @Test
    fun `only a fretted chord the tables lack needs the search, until it has been looked for`() {
        val unusual = ChordProChords.parse("Dbmaj9#11/Ab")!!
        assertTrue(!ChordVoicings.needsSearch(ChordProChords.parse("G")!!, ChordInstrument.GUITAR))
        assertTrue(!ChordVoicings.needsSearch(unusual, ChordInstrument.KEYBOARD))
        assertTrue(ChordVoicings.needsSearch(unusual, ChordInstrument.GUITAR))
        ChordVoicings.default(unusual, ChordInstrument.GUITAR)
        assertTrue(!ChordVoicings.needsSearch(unusual, ChordInstrument.GUITAR))
    }

    @Test
    fun `a chord with more notes than strings has no shape`() {
        val chord = ChordProChords.parse("C(b9,9,#9,11,#11,b13,13,#13,maj7)")!!
        listOf(ChordInstrument.GUITAR, ChordInstrument.UKULELE).forEach {
            assertEquals(emptyList(), ChordVoicings.all(chord, it))
            assertNull(ChordVoicings.default(chord, it))
        }
    }

    private fun first(name: String, instrument: ChordInstrument) = ChordVoicings.default(ChordProChords.parse(name)!!, instrument)?.let(ChordVoicings::write)

    private val corpus: List<String> = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").flatMap { root ->
        listOf("", "m", "7", "m7", "maj7", "sus2", "sus4", "7sus4", "6", "m6", "9", "add9", "dim", "dim7", "m7b5", "aug", "5", "/E", "13", "m9", "11")
            .map { root + it }
    }.filter { ChordProChords.parse(it) != null }

}
