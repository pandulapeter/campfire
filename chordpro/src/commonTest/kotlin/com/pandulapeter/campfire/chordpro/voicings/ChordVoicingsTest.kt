/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.voicings

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
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
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR), name) }
        mapOf(
            "C" to "0 0 0 3", "G" to "0 2 3 2", "Am" to "2 0 0 0", "F" to "2 0 1 0", "D" to "2 2 2 0", "Em" to "0 4 3 2",
            "A" to "2 1 0 0", "Dm" to "2 2 1 0", "G7" to "0 2 1 2", "C7" to "0 0 0 1", "Bb" to "3 2 1 1", "E7" to "1 2 0 2",
            "A7" to "0 1 0 0", "Bm" to "4 2 2 2", "D7" to "2 2 2 3", "F#m" to "2 1 2 0", "C#m" to "1 1 0 4", "E" to "1 4 0 2",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.UKULELE), name) }
        mapOf(
            "G" to "0 0 2 3", "C" to "0 2 3 0", "D" to "2 0 0 2", "Em" to "0 2 2 0", "Am" to "2 2 3 0", "A" to "2 2 4 0",
            "E" to "1 2 2 0", "F" to "2 3 3 1", "Bm" to "4 0 2 2", "G7" to "0 0 2 1", "D7" to "2 0 3 2", "B7" to "2 1 2 2",
            "C#" to "1 3 4 1", "C#m" to "1 2 4 4", "G#m" to "1 1 2 4", "Bbm" to "3 3 4 6",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.MANDOLIN), name) }
    }

    @Test
    fun `the banjo plays the shapes its charts show, its fifth string open only where its note is the chord's`() {
        mapOf(
            "G" to "0 0 0 0 0", "C" to "0 2 0 1 2", "D" to "x 0 2 3 4", "Em" to "0 2 0 0 2", "Am" to "x 2 2 1 2", "D7" to "x 0 2 1 4",
            "G7" to "0 0 0 0 3", "F" to "x 3 2 1 3", "A7" to "0 2 2 2 2", "E7" to "x 0 1 0 2", "Bm" to "x 0 4 3 4",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.BANJO), name) }
        assertEquals("0 5 5 4 5", first("Cm", ChordTuning.BANJO), "the Am shape moved, the fifth string's G ringing in Cm")
        assertEquals("x 3 3 2 3", first("Bbm", ChordTuning.BANJO), "the Am shape moved, the fifth string's G muted in Bbm")
        assertEquals("0 0 0 0 2", first("C", ChordTuning.BANJO_DOUBLE_C))
        assertEquals("0 2 0 0 0", first("G", ChordTuning.BANJO_C))
        assertEquals("0 0 0 0 0", first("D", ChordTuning.BANJO_OPEN_D))
        assertEquals("0 2 0 0 2", first("C", ChordTuning.BANJO_G_MODAL))
    }

    @Test
    fun `a shifted tuning draws the shape that sounds the chord on its strings`() {
        assertEquals("x 1 3 3 3 1", first("A", ChordTuning.GUITAR_HALF_STEP_DOWN), "the standard Bb shape")
        assertEquals("4 6 6 5 4 4", first("G", ChordTuning.GUITAR_HALF_STEP_DOWN), "the standard Ab shape")
        assertEquals("x 0 2 2 2 0", first("G", ChordTuning.GUITAR_FULL_STEP_DOWN), "the standard A shape")
        assertEquals("x 2 4 4 4 2", first("A", ChordTuning.GUITAR_FULL_STEP_DOWN), "the standard B shape")
        assertEquals("1 1 3 4", first("G", ChordTuning.MANDOLIN_HALF_STEP_DOWN), "the mandolin's Ab shape")
        assertEquals("0 2 3 2", first("G", ChordTuning.UKULELE_LOW_G))
        assertEquals("0 0 0 3", first("G", ChordTuning.UKULELE_BARITONE), "the soprano's C shape")
        assertEquals("2 0 1 0", first("G", ChordTuning.UKULELE_D), "the soprano's F shape")
    }

    @Test
    fun `every shape in every tuning sounds the chord it is drawn for`() {
        ChordTuning.entries.filter { it.instrument.isFretted }.forEach { tuning ->
            listOf("G", "A", "Am", "D7", "Em", "C", "F", "Bb", "D5").forEach { name ->
                val chord = ChordProChords.parse(name)!!
                ChordVoicings.all(chord, tuning).forEach { shape ->
                    val sounded = ChordShapeGeometry.pitchClasses(shape, tuning.strings)
                    assertTrue(chord.pitchClasses.containsAll(sounded) && chord.root in sounded, "$name in ${tuning.id}: ${ChordVoicings.write(shape)}")
                }
            }
        }
    }

    @Test
    fun `drop D plays the shapes its charts show`() {
        mapOf(
            "D" to "0 0 0 2 3 2", "G" to "5 2 0 0 0 3", "D5" to "0 0 0 x x x", "E5" to "2 2 2 x x x", "A" to "x 0 2 2 2 0",
            "Am" to "x 0 2 2 1 0", "C" to "x 3 2 0 1 0", "Em" to "2 2 2 0 0 0", "E" to "2 2 2 1 0 0", "F" to "3 3 3 2 1 1",
            "Dm" to "0 0 0 2 3 1", "Bm" to "x 2 4 4 3 2", "Cm" to "x 3 5 5 4 3", "G7" to "5 5 3 4 3 3", "Bb" to "x 1 3 3 3 1",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR_DROP_D), name) }
        assertEquals("0 0 0 x x x", first("C#5", ChordTuning.GUITAR_DROP_C_SHARP), "drop C#'s open power chord")
        assertEquals("1 1 1 x x x", first("D5", ChordTuning.GUITAR_DROP_C_SHARP))
        assertEquals("3 3 3 x x x", first("D5", ChordTuning.GUITAR_DROP_B))
        assertEquals("0 0 0 2 3 0", first("D5", ChordTuning.GUITAR_DOUBLE_DROP_D))
        assertEquals("0 0 0 2 3 4", first("D", ChordTuning.GUITAR_DOUBLE_DROP_D))
        assertEquals("5 2 0 0 0 5", first("G", ChordTuning.GUITAR_DOUBLE_DROP_D))
        assertEquals("0 0 0 2 3 2", first("C#", ChordTuning.GUITAR_DROP_C_SHARP), "drop C# reads drop D's table")
    }

    @Test
    fun `an open tuning plays the shapes its charts show`() {
        mapOf(
            "G" to "0 0 0 0 0 0", "Gsus4" to "0 0 0 0 1 0", "G7" to "0 0 0 0 0 3", "Em7" to "2 0 0 0 0 0", "C" to "x 5 5 5 5 5",
            "D" to "x x 0 2 3 4", "A" to "x 2 2 2 2 2", "Am" to "x 2 2 2 1 2", "Bb" to "x 3 3 3 3 3", "C7" to "x 5 5 5 5 8",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR_OPEN_G), name) }
        mapOf(
            "D" to "0 0 0 0 0 0", "Dsus4" to "0 0 0 1 0 0", "D7" to "0 0 0 0 3 0", "G" to "5 5 5 5 5 5", "A" to "7 7 7 7 7 7",
            "Bm7" to "x 2 0 0 0 0", "Em" to "2 2 2 1 2 2", "F#m" to "4 4 4 3 4 4",
        ).forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR_OPEN_D), name) }
        mapOf("C" to "0 0 0 0 0 0", "F" to "5 5 5 5 5 5", "G" to "7 7 7 7 7 7", "Am7" to "x 2 0 0 0 0", "Dm" to "2 2 2 2 2 1")
            .forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR_OPEN_C), name) }
        mapOf("Dsus4" to "0 0 0 0 0 0", "D5" to "0 0 0 2 0 0", "D" to "0 0 4 2 0 0", "Em7" to "2 2 0 0 2 0", "G" to "5 x 0 0 2 0")
            .forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.GUITAR_DADGAD), name) }
        mapOf("G5" to "0 0 0 0", "G" to "0 0 4 0", "D" to "2 4 2 0", "Em" to "0 2 4 2", "C" to "0 2 5 2")
            .forEach { (name, shape) -> assertEquals(shape, first(name, ChordTuning.MANDOLIN_GDGD), name) }
        assertEquals("0 0 0 0 0 0", first("E", ChordTuning.GUITAR_OPEN_E), "open E reads open D's table")
        assertEquals("0 0 0 0 0 0", first("A", ChordTuning.GUITAR_OPEN_A), "open A reads open G's table")
        assertEquals("0 0 0 0", first("A5", ChordTuning.MANDOLIN_AEAE), "AEAE reads GDGD's table")
    }

    @Test
    fun `a drop tuning's shapes are its table's, with their fingering`() {
        val g = ChordVoicings.default(ChordProChords.parse("G")!!, ChordTuning.GUITAR_DROP_D) as ChordVoicing.Fretted
        assertEquals(listOf(4, 1, 0, 0, 0, 2), g.fingers)
        val d = ChordVoicings.default(ChordProChords.parse("D")!!, ChordTuning.GUITAR_DOUBLE_DROP_D) as ChordVoicing.Fretted
        assertEquals(listOf(0, 0, 0, 1, 2, 4), d.fingers)
    }

    @Test
    fun `the shapes are remembered per tuning`() {
        val g = ChordProChords.parse("G")!!
        assertEquals("3 2 0 0 0 3", ChordVoicings.write(ChordVoicings.default(g, ChordTuning.GUITAR)!!))
        assertEquals("5 2 0 0 0 3", ChordVoicings.write(ChordVoicings.default(g, ChordTuning.GUITAR_DROP_D)!!))
        assertEquals("3 2 0 0 0 3", ChordVoicings.write(ChordVoicings.default(g, ChordTuning.GUITAR)!!))
    }

    @Test
    fun `every shape the search finds keeps its rules`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordTuning.entries.filter { it.instrument.isFretted }.forEach { tuning ->
                val instrument = tuning.instrument
                val played = if (instrument.playsChordOver) chord.copy(bass = null) else chord
                val shapes = ChordVoicings.all(chord, tuning)
                assertEquals(shapes.size, shapes.map { (it as ChordVoicing.Fretted).frets }.distinct().size, "$name has no shape twice")
                shapes.forEach { voicing ->
                    val shape = assertIs<ChordVoicing.Fretted>(voicing)
                    val frets = shape.frets
                    val label = "$name in ${tuning.id}: ${ChordVoicings.write(shape)}"
                    assertEquals(tuning.strings.size, frets.size, label)
                    assertTrue(played.pitchClasses.containsAll(ChordShapeGeometry.pitchClasses(shape, tuning.strings)), label)
                    assertTrue(ChordShapeGeometry.fingerCount(frets) <= 4, label)
                    val sounding = frets.indices.filter { frets[it] != null }
                    // The tables' own shapes, which carry their fingering, may mute a string with a finger that rests on it.
                    if (shape.fingers == null) assertEquals(sounding.size, sounding.last() - sounding.first() + 1, "$label mutes no string between two that sound")
                    val stopped = frets.filterNotNull().filter { it > 0 }
                    if (stopped.isNotEmpty()) assertTrue(stopped.max() - stopped.min() < 4, label)
                    instrument.droneString?.let { assertTrue(frets[it] == null || frets[it] == 0, "$label leaves its drone string open or muted") }
                    if (!instrument.playsChordOver) {
                        val bottom = sounding.minBy { tuning.strings[it] + frets[it]!! }
                        val lowest = (tuning.strings[bottom] + frets[bottom]!!) % 12
                        val isOpenFifth = tuning.isOpen && chord.bass == null && 7 in chord.intervals && lowest == (chord.root + 7) % 12 && frets[bottom] == 0
                        assertTrue(lowest == (chord.bass ?: chord.root) || isOpenFifth, "$label sounds its bass or root lowest, or an open tuning's open string its fifth")
                    }
                }
            }
        }
    }

    @Test
    fun `the search answers the same twice`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordTuning.entries.forEach { assertEquals(ChordVoicings.all(chord, it), ChordVoicings.all(chord, it), name) }
        }
    }

    @Test
    fun `the default is the first of all the shapes`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordTuning.entries.forEach {
                assertEquals(ChordVoicings.all(chord, it).firstOrNull(), ChordVoicings.default(chord, it), name)
                assertEquals(ChordVoicings.all(chord, it).firstOrNull(), ChordVoicings.default(chord, it), "$name, remembered")
            }
        }
    }

    @Test
    fun `the keyboard plays the notes from the root up and their inversions`() {
        assertEquals(
            listOf(ChordVoicing.Keys(listOf(0, 4, 7)), ChordVoicing.Keys(listOf(4, 7, 12)), ChordVoicing.Keys(listOf(7, 12, 16))),
            ChordVoicings.all(ChordProChords.parse("C")!!, ChordTuning.KEYBOARD),
        )
        assertEquals(ChordVoicing.Keys(listOf(9, 12, 16, 19)), ChordVoicings.default(ChordProChords.parse("Am7")!!, ChordTuning.KEYBOARD))
        assertEquals(ChordVoicing.Keys(listOf(14, 18, 21), bass = 6), ChordVoicings.default(ChordProChords.parse("D/F#")!!, ChordTuning.KEYBOARD))
        assertEquals(6, (ChordVoicings.default(ChordProChords.parse("C7(b9,#9,#11,b13)")!!, ChordTuning.KEYBOARD) as ChordVoicing.Keys).notes.size)
        val thirteenth = ChordVoicings.default(ChordProChords.parse("C13#11")!!, ChordTuning.KEYBOARD) as ChordVoicing.Keys
        assertEquals(5, thirteenth.notes.size, "the fifth goes first")
    }

    @Test
    fun `a stored shape is read back as it was written`() {
        corpus.forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ChordTuning.entries.forEach { tuning ->
                ChordVoicings.all(chord, tuning).forEach { shape ->
                    assertEquals(shape, ChordVoicings.read(ChordVoicings.write(shape), tuning, chord), name)
                }
            }
        }
        assertEquals("x 3 2 0 1 0", ChordVoicings.write(ChordVoicing.Fretted(listOf(null, 3, 2, 0, 1, 0))))
        assertEquals("12 16 19 / 4", ChordVoicings.write(ChordVoicing.Keys(listOf(12, 16, 19), bass = 4)))
        assertEquals(ChordVoicing.Fretted(listOf(null, 3, 2, 0, 1, 0)), ChordVoicings.read("x 3 2 0 1 0", ChordTuning.GUITAR))
        assertEquals(listOf(0, 3, 2, 0, 1, 0), (ChordVoicings.read("x 3 2 0 1 0", ChordTuning.GUITAR, ChordProChords.parse("C")) as ChordVoicing.Fretted).fingers)
        listOf("0 4 48", "0 4 99999999", "4 7 / -5", "4 7 / 12").forEach { assertNull(ChordVoicings.read(it, ChordTuning.KEYBOARD), it) }
        listOf("0 4 47", "4 7 / 11").forEach { assertNotNull(ChordVoicings.read(it, ChordTuning.KEYBOARD), it) }
        listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").forEach { root ->
            listOf("", "m7", "maj9", "13#11", "/E").forEach { quality ->
                val chord = ChordProChords.parse(root + quality)!!
                ChordVoicings.all(chord, ChordTuning.KEYBOARD).forEach { shape ->
                    assertEquals(shape, ChordVoicings.read(ChordVoicings.write(shape), ChordTuning.KEYBOARD, chord), root + quality)
                }
            }
        }
    }

    @Test
    fun `a stored shape moved from another root keeps the table's fingering`() {
        val moved = listOf("C", "Am", "G7", "Fmaj7", "Bbm7b5", "F#m7", "Ebmaj7").flatMap { name ->
            val chord = ChordProChords.parse(name)!!
            val filed = ChordVoicingTables.guitar.map { ChordProDefinitions.read(it, ChordInstrument.GUITAR.id) as ChordProDefinitions.Reading.Shape }
                .filter { ChordProChords.parse(it.name) == chord }
                .map { (it.voicing as ChordVoicing.Fretted).frets }
            ChordVoicingSearch.tableShapes(chord, ChordTuning.GUITAR).filter { it.frets !in filed }.map { chord to it }
        }
        assertTrue(moved.isNotEmpty())
        moved.forEach { (chord, shape) ->
            assertNotNull(shape.fingers, shape.toString())
            assertEquals(shape, ChordVoicings.read(ChordVoicings.write(shape), ChordTuning.GUITAR, chord))
        }
    }

    @Test
    fun `the table lookup of one shape answers what the chord's table shapes do`() {
        val tunings = listOf(ChordTuning.GUITAR, ChordTuning.GUITAR_DROP_D, ChordTuning.GUITAR_HALF_STEP_DOWN, ChordTuning.UKULELE_BARITONE, ChordTuning.BANJO)
        var searched = 0
        listOf("C", "Am", "G7", "Fmaj7", "C/E", "Bbm7b5").forEach { name ->
            val chord = ChordProChords.parse(name)!!
            tunings.forEach { tuning ->
                val table = ChordVoicingSearch.tableShapes(chord, tuning)
                ChordVoicings.all(chord, tuning).map { (it as ChordVoicing.Fretted).frets }.forEach { frets ->
                    val expected = table.firstOrNull { it.frets == frets }
                    if (expected == null) searched++
                    assertEquals(expected, ChordVoicingSearch.tableShape(chord, tuning, frets), "$name in ${tuning.id}: $frets")
                }
            }
        }
        assertTrue(searched > 0, "a searched shape is looked up too")
    }

    @Test
    fun `a shape of another instrument is not read`() {
        assertNull(ChordVoicings.read("x 3 2 0 1 0", ChordTuning.UKULELE))
        assertNull(ChordVoicings.read("0 0 0 3", ChordTuning.GUITAR))
        assertNull(ChordVoicings.read("x 3 two 0 1 0", ChordTuning.GUITAR))
        assertNull(ChordVoicings.read("", ChordTuning.KEYBOARD))
        assertNull(ChordVoicings.read("4 7 /", ChordTuning.KEYBOARD))
    }

    @Test
    fun `a chord with no shape answers no shape rather than a wrong one`() {
        val crowded = ChordProChords.parse("C7(b9, #9, #11, b13)")!!
        assertEquals(emptyList(), ChordVoicings.all(crowded, ChordTuning.UKULELE))
        assertNull(ChordVoicings.default(crowded, ChordTuning.UKULELE))
    }

    @Test
    fun `only a fretted chord the tables lack needs the search, until it has been looked for`() {
        val unusual = ChordProChords.parse("Dbmaj9#11/Ab")!!
        assertTrue(!ChordVoicings.needsSearch(ChordProChords.parse("G")!!, ChordTuning.GUITAR))
        assertTrue(!ChordVoicings.needsSearch(unusual, ChordTuning.KEYBOARD))
        assertTrue(ChordVoicings.needsSearch(unusual, ChordTuning.GUITAR))
        ChordVoicings.default(unusual, ChordTuning.GUITAR)
        assertTrue(!ChordVoicings.needsSearch(unusual, ChordTuning.GUITAR))
    }

    @Test
    fun `a chord with more notes than strings has no shape`() {
        val chord = ChordProChords.parse("C(b9,9,#9,11,#11,b13,13,#13,maj7)")!!
        listOf(ChordTuning.GUITAR, ChordTuning.UKULELE).forEach {
            assertEquals(emptyList(), ChordVoicings.all(chord, it))
            assertNull(ChordVoicings.default(chord, it))
        }
    }

    private fun first(name: String, tuning: ChordTuning) = ChordVoicings.default(ChordProChords.parse(name)!!, tuning)?.let(ChordVoicings::write)

    private val corpus: List<String> = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").flatMap { root ->
        listOf("", "m", "7", "m7", "maj7", "sus2", "sus4", "7sus4", "6", "m6", "9", "add9", "dim", "dim7", "m7b5", "aug", "5", "/E", "13", "m9", "11")
            .map { root + it }
    }.filter { ChordProChords.parse(it) != null }
}
