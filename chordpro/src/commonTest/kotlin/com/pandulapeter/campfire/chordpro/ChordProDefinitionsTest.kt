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
        listOf("G copy G7", "G copyall G7", "G display G/B", "G frets 0 0 0 0 0", "G").forEach {
            assertIs<ChordProDefinitions.Reading.Other>(ChordProDefinitions.read(it), it)
        }
        listOf("G frets 3 2 x two 0 3", "G frets", "G frets 3 2 0 0 0 3 fingers 1 2 3", "G base-fret 0 frets 3 2 0 0 0 3", "G keys a b").forEach {
            assertEquals(ChordProDefinitions.Reading.Invalid, ChordProDefinitions.read(it), it)
        }
        assertEquals(emptyList(), definitions("{chord: Am}\n{define: G copy G7}"))
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
        ).forEach { (name, voicing) ->
            val line = ChordProDefinitions.line(name, voicing)
            val read = definitions(line).single()
            assertEquals(name, read.name, line)
            if (voicing is ChordVoicing.Keys) {
                assertEquals((listOfNotNull(voicing.bass) + voicing.notes).sorted(), (read.voicing as ChordVoicing.Keys).notes, line)
            } else {
                assertEquals(voicing, read.voicing, line)
            }
        }
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
        val text = "{define: G frets 3 2 0 0 0 3}\n{define: X frets 3 2 0 0 0 3}\n{define: G frets 3 two}\n{define-ukulele: G frets 3 2 0 0 0 3}"
        val tokens = ChordProHighlighter.tokenize(text)
        val chords = tokens.filter { it.type == ChordProHighlighter.TokenType.CHORD }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("G"), chords)
        val invalid = tokens.filter { it.type == ChordProHighlighter.TokenType.INVALID }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("{define: G frets 3 two}", "{define-ukulele: G frets 3 2 0 0 0 3}"), invalid)
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
}
