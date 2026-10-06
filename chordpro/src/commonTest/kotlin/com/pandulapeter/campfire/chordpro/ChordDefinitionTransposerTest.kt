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

import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordDefinitionTransposerTest {

    private val tableDefinitions = mapOf(
        ChordInstrument.GUITAR to ChordVoicingTables.guitar,
        ChordInstrument.UKULELE to ChordVoicingTables.ukulele,
    ).flatMap { (_, lines) -> lines.map { (ChordProDefinitions.read(it) as ChordProDefinitions.Reading.Shape).let { shape -> ChordDefinition(shape.name, shape.instrument, shape.voicing) } } }

    private fun move(definition: ChordDefinition, semitones: Int) =
        ChordProDefinitions.transposed(definition, semitones) { ChordProTransposer.transposeChord(it, semitones, preferFlats = false) }

    @Test
    fun `every shape of the tables still sounds the chord its new name spells`() {
        tableDefinitions.forEach { definition ->
            (-5..6).forEach { semitones ->
                val moved = move(definition, semitones)
                val chord = ChordProChords.parse(moved.name)!!.let { if (definition.instrument == ChordInstrument.UKULELE) it.copy(bass = null) else it }
                val sounded = ChordVoicings.pitchClasses(moved.voicing, definition.instrument)
                assertTrue(chord.pitchClasses.containsAll(sounded), "${definition.name} $semitones -> ${moved.name} ${ChordVoicings.write(moved.voicing)}")
                assertEquals(
                    ChordVoicings.pitchClasses(definition.voicing, definition.instrument).map { (it + semitones).mod(12) }.toSet(),
                    sounded,
                    "${definition.name} $semitones",
                )
            }
        }
    }

    @Test
    fun `there and back is the shape it started as`() {
        tableDefinitions.forEach { definition ->
            (-11..11).forEach { semitones ->
                val back = move(move(definition, semitones), -semitones)
                assertEquals((definition.voicing as ChordVoicing.Fretted).frets, (back.voicing as ChordVoicing.Fretted).frets, "${definition.name} $semitones")
                assertEquals(0, back.movedBy, "${definition.name} $semitones")
                // The fingering survives wherever it never needed a fifth finger on the way.
                if ((move(definition, semitones).voicing as ChordVoicing.Fretted).fingers != null) {
                    assertEquals(definition.voicing.fingers, back.voicing.fingers, "${definition.name} $semitones")
                }
            }
        }
    }

    @Test
    fun `a shape with an open string moves up the neck, also when transposed down`() {
        val e = ChordDefinition("E", ChordInstrument.GUITAR, ChordVoicing.Fretted(listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0)))
        val f = move(e, 1)
        assertEquals("F", f.name)
        assertEquals(ChordVoicing.Fretted(listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1)), f.voicing)
        assertEquals(1, f.movedBy)
        val eb = move(e, -1)
        assertEquals(listOf<Int?>(11, 13, 13, 12, 11, 11), (eb.voicing as ChordVoicing.Fretted).frets)
        assertEquals(11, eb.movedBy)
    }

    @Test
    fun `a barre high on the neck comes down rather than running off it`() {
        val bb = ChordDefinition("Bb", ChordInstrument.GUITAR, ChordVoicing.Fretted(listOf(6, 8, 8, 7, 6, 6), listOf(1, 3, 4, 2, 1, 1)))
        val e = move(bb, 6)
        assertEquals(listOf<Int?>(0, 2, 2, 1, 0, 0), (e.voicing as ChordVoicing.Fretted).frets)
        assertEquals(listOf(0, 2, 3, 1, 0, 0), e.voicing.fingers, "the barre's strings come to rest open")
        assertEquals(-6, e.movedBy)
        val high = ChordDefinition("A", ChordInstrument.GUITAR, ChordVoicing.Fretted(listOf(null, 12, 14, 14, 14, 12)))
        assertEquals(listOf<Int?>(null, 6, 8, 8, 8, 6), (move(high, 6).voicing as ChordVoicing.Fretted).frets)
    }

    @Test
    fun `a keyboard's keys move with their root`() {
        val c = ChordDefinition("C", ChordInstrument.KEYBOARD, ChordVoicing.Keys(listOf(0, 4, 7)))
        assertEquals(ChordVoicing.Keys(listOf(2, 6, 9)), move(c, 2).voicing)
        assertEquals(ChordVoicing.Keys(listOf(10, 14, 17)), move(c, -2).voicing)
        assertEquals(0, move(c, 2).movedBy)
    }

    @Test
    fun `the model moves a song's definitions with the song`() {
        val song = ChordProParser.parse("{define: E frets 0 2 2 1 0 0}\n[E]la")
        val moved = ChordProTransposer.transpose(song, 1)
        assertEquals("F", moved.metadata.definitions.single().name)
        assertEquals(1, moved.metadata.definitions.single().movedBy)
        val opening = ChordProParser.parse("{transpose: 2}\n{define: E frets 0 2 2 1 0 0}\n[E]la")
        assertEquals("F#", ChordProTransposer.transpose(opening, opening.metadata.transpose).metadata.definitions.single().name)
        assertEquals("E", ChordProTransposer.transpose(song, 0, preferFlats = true).metadata.definitions.single().name)
    }

    @Test
    fun `the text keeps every character outside the name, the base fret, the frets and the fingers`() {
        assertEquals(
            "{define-guitar:  F   frets  1 3 3 2 1 1  fingers 1 3 4 2 1 1 display F}",
            ChordProTransposer.transposeText("{define-guitar:  E   frets  0 2 2 1 0 0  fingers 0 2 3 1 0 0 display F}", 1),
        )
        assertEquals("{define: F# base-fret 9 frets N 1 3 3 3 1}", ChordProTransposer.transposeText("{define: E base-fret 7 frets N 1 3 3 3 1}", 2))
        assertEquals("{define: D keys 0 4 7}", ChordProTransposer.transposeText("{define: C keys 0 4 7}", 2))
        assertEquals("{chord: Bm}\n{define: D copy A}", ChordProTransposer.transposeText("{chord: Am}\n{define: C copy G}", 2))
    }

    @Test
    fun `the text keeps a fingering up to five fingers, and one the move cannot carry back goes with its keyword`() {
        assertEquals(
            "{define: D frets 2 5 4 2 3 5 fingers 1 4 3 1 2 5}",
            ChordProTransposer.transposeText("{define: C frets 0 3 2 0 1 3 fingers 0 3 2 0 1 4}", 2),
        )
        assertEquals("{define: C frets x 0 0 1 2 x}", ChordProTransposer.transposeText("{define: C# frets x 1 1 2 3 x fingers 0 1 2 3 4 0}", -1))
        val moved = ChordProTransposer.transpose(ChordProParser.parse("{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}\n[G]x"), 1)
        assertEquals(null, (moved.metadata.definitions.single().voicing as ChordVoicing.Fretted).fingers)
    }

    @Test
    fun `a line that cannot be read is left byte for byte`() {
        assertEquals("{define: C frets x 3 two 0 1 0}\n[D]x", ChordProTransposer.transposeText("{define: C frets x 3 two 0 1 0}\n[C]x", 2))
        assertEquals(
            "{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}\n[Ab]x",
            ChordProTransposer.transposeText("{define: G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3}\n[G]x", 1),
        )
        assertEquals(
            "{define: A base-fret 3 frets x 1 3 3 3 1 base-fret 5}\n[B]x",
            ChordProTransposer.transposeText("{define: A base-fret 3 frets x 1 3 3 3 1 base-fret 5}\n[A]x", 2),
        )
    }

    @Test
    fun `a thumb's finger and the base_fret spelling are kept as they are written`() {
        assertEquals("{define: G frets 3 5 5 4 3 3 fingers T 3 4 2 1 1}", ChordProTransposer.transposeText("{define: F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1}", 2))
        assertEquals("{define: B base_fret 7 frets x 1 3 3 3 1}", ChordProTransposer.transposeText("{define: A base_fret 5 frets x 1 3 3 3 1}", 2))
    }

    @Test
    fun `there and back in the text is the line it started as`() {
        val text = "{define: C base-fret 1 frets x 3 2 0 1 0 fingers 0 3 2 0 1 0}\n{define-ukulele: Am frets 2 0 0 0}\n{define: G keys 0 4 7}\n" +
            "{define: G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4}\n[C]x [Am]y [G]z"
        (-5..6).forEach { semitones ->
            assertEquals(text, ChordProTransposer.transposeText(ChordProTransposer.transposeText(text, semitones), -semitones), "$semitones")
        }
    }

    @Test
    fun `the caret stays on a definition's line`() {
        val before = "{define: C frets x 3 2 0 1 0}\n[C]x"
        val after = ChordProTransposer.transposeText(before, 2)
        val offset = ChordProTransposer.transposedOffset(before, after, 20)
        assertTrue(offset < after.indexOf('\n'))
    }
}
