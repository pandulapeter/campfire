/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProHighlighter
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSerializer
import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChordProDefinitionsTest {

    private fun definitions(text: String) = ChordProParser.parse(text).metadata.definitions

    @Test
    fun `a fretted shape is read from its base fret, and its instrument from its frets`() {
        assertEquals(
            listOf(
                ChordDefinition("G", ChordInstrument.GUITAR, ChordVoicing.Fretted(listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3))),
                ChordDefinition("Bb", ChordInstrument.GUITAR, ChordVoicing.Fretted(listOf(null, 6, 8, 8, 8, 6))),
                ChordDefinition("C", ChordInstrument.UKULELE, ChordVoicing.Fretted(listOf(0, 0, 0, 3))),
            ),
            definitions(
                "{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}\n" +
                    "{define: Bb base-fret 6 frets x 1 3 3 3 1}\n" +
                    "{chord: C frets 0 0 0 3}\n[G]la",
            ),
        )
        assertEquals(listOf(null, null, 0, 2, 3, 2), (definitions("{define D frets N -1 0 2 3 2}").single().voicing as ChordVoicing.Fretted).frets)
    }

    @Test
    fun `a keyboard shape is counted from the root`() {
        assertEquals(
            listOf(ChordDefinition("D", ChordInstrument.KEYBOARD, ChordVoicing.Keys(listOf(2, 6, 9)))),
            definitions("{define: D keys 0 4 7}"),
        )
        assertEquals(ChordVoicing.Keys(listOf(4, 7, 12)), definitions("{define: C keys -8 -5 0}").single().voicing)
    }

    @Test
    fun `a keyboard shape is counted from the root its chord has in the notation the song is read in`() {
        assertEquals(ChordVoicing.Keys(listOf(10, 14, 17)), ChordProParser.parse("{define: B keys 0 4 7}\n[H]x [B]y").metadata.definitions.single().voicing)
        assertEquals(ChordVoicing.Keys(listOf(10, 14, 17)), ChordProParser.parse("{define: B keys 0 4 7}\n[B]y", ChordNotation.GERMAN).metadata.definitions.single().voicing)
        assertEquals(ChordVoicing.Keys(listOf(11, 15, 18)), ChordProParser.parse("{define: H keys 0 4 7}\n[H]y", ChordNotation.GERMAN).metadata.definitions.single().voicing)
        mapOf(
            "B" to ChordVoicing.Keys(listOf(10, 14, 17)),
            "C/B" to ChordVoicing.Keys(listOf(12, 16, 19), bass = 10),
        ).forEach { (name, voicing) ->
            val line = ChordProDefinitions.line(name, voicing, ChordNotation.GERMAN)
            assertEquals(voicing, ChordProParser.parse("$line\n[$name]x", ChordNotation.GERMAN).metadata.definitions.single().voicing, line)
        }
    }

    @Test
    fun `a selector names the instrument, and one that contradicts the shape is unreadable`() {
        assertEquals(ChordInstrument.UKULELE, definitions("{define-ukulele: C frets 0 0 0 3}").single().instrument)
        assertEquals(ChordInstrument.KEYBOARD, definitions("{define-piano: C keys 0 4 7}").single().instrument)
        assertEquals(emptyList(), definitions("{define-ukulele: G frets 3 2 0 0 0 3}"))
        assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read("G frets 3 2 0 0 0 3", "ukulele"))
        assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read("G keys 0 4 7", "guitar"))
        assertIs<ChordProDefinitions.Reading.Other>(ChordProDefinitions.read("G frets 3 2 0 0 0 3", "pete"))
        assertEquals(emptyList(), definitions("{define-pete: G frets 3 2 0 0 0 3}"))
    }

    @Test
    fun `what declares no shape is no definition, and what cannot be read is invalid`() {
        listOf("G copy G7", "G copyall G7", "G display G/B", "G frets 0 0 0 0 0", "G", "G copy G7 frets 3 2 0 0 0 3").forEach {
            assertIs<ChordProDefinitions.Reading.Other>(ChordProDefinitions.read(it), it)
        }
        listOf(
            "G frets 3 2 x two 0 3",
            "G frets",
            "G frets 3 2 0 0 0 3 fingers 1 2 3",
            "G base-fret 0 frets 3 2 0 0 0 3",
            "G keys a b",
            "G frets 3 2 0 0 0 25",
            "G frets 3 2 0 0 0 99999999",
            "G base-fret 25 frets 1 1 1 1 1 1",
            "G base-fret 2147483647 frets 3 2 1 1 1 3",
            "G base-fret 22 frets 1 3 3 2 1 4",
            "G frets 1 2 3 4 5 6 7 frets 3 2 0 0 0 3",
            "A base-fret 3 frets x 1 3 3 3 1 base-fret 5",
            "G frets 3 2 0 0 0 3 fingers 3 2 0 0 0 4 fingers 3 2 0 0 0 4",
            "C keys 0 4 7 keys 0 3 7",
        ).forEach {
            assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read(it), it)
        }
        assertEquals(emptyList(), definitions("{chord: Am}\n{define: G copy G7}"))
    }

    @Test
    fun `a finger that is no number Campfire draws is shown as none, and base_fret is read as base-fret`() {
        listOf("F frets 1 3 3 2 1 1 fingers T 3 4 2 1 1", "F frets 1 3 3 2 1 1 fingers 9 3 4 2 1 1").forEach {
            val voicing = assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read(it), it).voicing as ChordVoicing.Fretted
            assertEquals(listOf(0, 3, 4, 2, 1, 1), voicing.fingers, it)
        }
        assertEquals(
            ChordVoicing.Fretted(listOf(0, 0, 2, 2, 2, 0)),
            assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read("A frets 0 0 2 2 2 0 base_fret 1")).voicing,
        )
        assertEquals(
            listOf(null, 5, 7, 7, 7, 5),
            (assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read("A base_fret 5 frets x 1 3 3 3 1")).voicing as ChordVoicing.Fretted).frets,
        )
        assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read("F frets 1 3 3 2 1 1 fingers 1 2 3"))
        assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read("A base-fret 5 frets x 1 3 3 3 1 base_fret 5"))
    }

    @Test
    fun `keys past the diagram are wrapped by their note`() {
        mapOf(
            "C keys 0 4 99999999" to listOf(0, 4, 99999999),
            "C keys -99999999 4 7" to listOf(-99999999, 4, 7),
        ).forEach { (value, written) ->
            val notes = (assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read(value)).voicing as ChordVoicing.Keys).notes
            assertTrue(notes.all { it in 0..47 }, value)
            assertEquals(written.map { it.mod(12) }.toSet(), notes.map { it.mod(12) }.toSet(), value)
        }
        assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read("G base-fret 22 frets 1 3 3 2 1 3"))
    }

    @Test
    fun `the last shape of a chord on an instrument wins, in the place of the first`() {
        assertEquals(
            listOf(listOf<Int?>(3, 2, 0, 0, 3, 3), listOf<Int?>(0, 2, 3, 2)),
            definitions(
                "{define: G frets 3 2 0 0 0 3}\n{define: G frets 0 2 3 2}\n{define: G frets 3 2 0 0 3 3}",
            ).map { (it.voicing as ChordVoicing.Fretted).frets },
        )
    }

    @Test
    fun `a define outranks a chord directive, which counts only where the song defines none`() {
        fun frets(text: String) = definitions(text).map { (it.voicing as ChordVoicing.Fretted).frets }
        assertEquals(listOf(listOf<Int?>(3, 2, 0, 0, 0, 3)), frets("{define: G frets 3 2 0 0 0 3}\n{chord: G frets 3 x 0 0 3 3}"))
        assertEquals(
            listOf(listOf<Int?>(3, 2, 0, 0, 0, 3), listOf<Int?>(0, 2, 3, 2)),
            frets("{chord: G frets 3 x 0 0 3 3}\n{define: G frets 0 2 3 2}\n{define: G frets 3 2 0 0 0 3}"),
        )
        assertEquals(listOf(listOf<Int?>(3, 2, 0, 0, 3, 3)), frets("{chord: G frets 3 x 0 0 3 3}\n{chord: G frets 3 2 0 0 3 3}"))
        val text = "{define: G frets 3 2 0 0 0 3}\n{chord: G frets 3 x 0 0 3 3}"
        assertEquals("3 2 0 0 0 3", ChordProDefinitions.rangeOf(text, "G", ChordInstrument.GUITAR)?.let { text.substring(it) })
    }

    @Test
    fun `a definition inside an environment handed to another program is that program's text`() {
        assertEquals(emptyList(), definitions("{start_of_abc}\n{define: G frets 3 2 0 0 0 3}\n{end_of_abc}"))
    }

    @Test
    fun `the line written for a shape reads back as it`() {
        listOf(
            "G" to ChordVoicing.Fretted(listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3)),
            "Bb" to ChordVoicing.Fretted(listOf(null, 6, 8, 8, 8, 6)),
            "D" to ChordVoicing.Fretted(listOf(null, null, 0, 7, 7, 5)),
            "C" to ChordVoicing.Fretted(listOf(0, 0, 0, 3)),
            "D" to ChordVoicing.Keys(listOf(2, 6, 9)),
            "D/F#" to ChordVoicing.Keys(listOf(14, 18, 21), bass = 6),
            "C/D" to ChordVoicing.Keys(listOf(12, 16, 19), bass = 2),
        ).forEach { (name, voicing) ->
            val line = ChordProDefinitions.line(name, voicing)
            val read = definitions(line).single()
            assertEquals(name, read.name, line)
            assertEquals(voicing, read.voicing, line)
        }
        assertEquals(ChordVoicing.Keys(listOf(4, 7, 12)), definitions("{define: C/E keys 4 7 12}").single().voicing)
        assertEquals("{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}", ChordProDefinitions.line("G", ChordVoicing.Fretted(listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3))))
        assertEquals("{define: Bb base-fret 6 frets x 1 3 3 3 1}", ChordProDefinitions.line("Bb", ChordVoicing.Fretted(listOf(null, 6, 8, 8, 8, 6))))
        assertEquals("{define: D keys 0 4 7}", ChordProDefinitions.line("D", ChordVoicing.Keys(listOf(2, 6, 9))))
    }

    @Test
    fun `the serializer writes the definitions back`() {
        val text = "{title: X}\n{define: G frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}\n{define-ukulele: C frets 0 0 0 3}\n{define: D keys 0 4 7}\n\n[G]la [C]la [D]la"
        val parsed = ChordProParser.parse(text)
        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    @Test
    fun `the highlighter colours the chord a definition names, and a line it cannot read`() {
        val text = "{define: G frets 3 2 0 0 0 3}\n{define: X frets 3 2 0 0 0 3}\n{define: G frets 3 two}\n{define-ukulele: G frets 3 2 0 0 0 3}\n{define: G base-fret 2147483647 frets 3 2 1 1 1 3}"
        val tokens = ChordProHighlighter.tokenize(text)
        val chords = tokens.filter { it.type == ChordProHighlighter.TokenType.CHORD }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("G"), chords)
        val invalid = tokens.filter { it.type == ChordProHighlighter.TokenType.INVALID }.map { text.substring(it.start, it.end) }
        assertEquals(
            listOf("{define: G frets 3 two}", "{define-ukulele: G frets 3 2 0 0 0 3}", "{define: G base-fret 2147483647 frets 3 2 1 1 1 3}"),
            invalid,
        )
    }

    @Test
    fun `a change of notation renames a definition, a selected one included`() {
        assertEquals(
            "{define: H frets x 2 4 4 4 2}\n{define-ukulele: B frets 3 2 1 1}\n[H]la",
            ChordProNotation.convertText("{define: B frets x 2 4 4 4 2}\n{define-ukulele: Bb frets 3 2 1 1}\n[B]la", ChordNotation.STANDARD, ChordNotation.GERMAN),
        )
        val song = ChordProNotation.toNotation(ChordProParser.parse("{define: B frets x 2 4 4 4 2}\n[B]la"), ChordNotation.GERMAN)
        assertEquals("H", song.metadata.definitions.single().name)
        assertTrue(ChordProParser.parse("{define: H frets x 2 4 4 4 2}\n[B]la").metadata.definitions.single().name in setOf("H", "B"))
    }

    @Test
    fun `the chord at the caret is the one in the brackets it is in or touching`() {
        val text = "{title: X}\n[G]Hello [Am7]there [*softly] []la"
        val line = text.indexOf('[')
        assertEquals("G", ChordProDefinitions.chordAt(text, line))
        assertEquals("G", ChordProDefinitions.chordAt(text, line + 2))
        assertEquals("G", ChordProDefinitions.chordAt(text, line + 3))
        assertEquals(null, ChordProDefinitions.chordAt(text, line + 5))
        assertEquals("Am7", ChordProDefinitions.chordAt(text, text.indexOf("Am7") + 1))
        assertEquals(null, ChordProDefinitions.chordAt(text, text.indexOf("softly")))
        assertEquals(null, ChordProDefinitions.chordAt(text, text.indexOf("[]") + 1))
        assertEquals(null, ChordProDefinitions.chordAt(text, 3))
    }

    @Test
    fun `the shape of a chord defined in the text is found for the caret to go to`() {
        val text = "{title: X}\n{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}\n  {define-ukulele: G frets 0 2 3 2}\n{define: C keys 0 4 7}\n[G]la"
        assertEquals("3 2 0 0 0 3", text.substring(ChordProDefinitions.rangeOf(text, "G", ChordInstrument.GUITAR)!!))
        assertEquals("0 2 3 2", text.substring(ChordProDefinitions.rangeOf(text, "G", ChordInstrument.UKULELE)!!))
        assertEquals("0 4 7", text.substring(ChordProDefinitions.rangeOf(text, "C", ChordInstrument.KEYBOARD)!!))
        assertEquals(null, ChordProDefinitions.rangeOf(text, "C", ChordInstrument.GUITAR))
    }
}
