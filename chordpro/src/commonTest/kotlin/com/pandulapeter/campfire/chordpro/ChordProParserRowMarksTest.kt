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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Chord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordProParserRowMarksTest {

    private fun line(text: String) = (ChordProParser.parse(text).blocks.single() as ChordProBlock.Section).lines.single() as ChordProLine.Lyrics

    private fun mark(position: Int, name: String) = Chord(position = position, name = name, isAnnotation = true, isMark = true)

    private fun chord(position: Int, name: String) = Chord(position = position, name = name, isAnnotation = false)

    private fun assertRoundTrip(text: String) {
        val song = ChordProParser.parse(text)
        assertEquals(song, ChordProParser.parse(ChordProSerializer.serialize(song)))
    }

    @Test
    fun `a repeat count after a row of chords is a mark on that row and leaves no lyric`() {
        val line = line("[G] [C] [D] x4")

        assertTrue(line.text.isBlank())
        assertEquals("     ", line.text)
        assertEquals(listOf(chord(0, "G"), chord(1, "C"), chord(2, "D"), mark(3, "x4")), line.chords)
        assertRoundTrip("[G] [C] [D] x4")
    }

    @Test
    fun `every form of a repeat count is a mark, kept as written`() {
        listOf("x4", "X4", "4x", "4X", "×4", "4×", "(x4)", "(4x)", "(×4)").forEach { count ->
            assertEquals(listOf(chord(0, "G"), chord(1, "C"), mark(2, count)), line("[G] [C] $count").chords, count)
        }
    }

    @Test
    fun `bar lines, repeat signs, and a no chord written as text are marks between the chords`() {
        val line = line("[Am] | [F] | % %% |: :| :|: || / - N.C. NC x2")

        assertTrue(line.text.isBlank())
        assertEquals(
            listOf("Am", "|", "F", "|", "%", "%%", "|:", ":|", ":|:", "||", "/", "-", "N.C.", "NC", "x2"),
            line.chords.map { it.name },
        )
        assertTrue(line.chords.filter { it.name != "Am" && it.name != "F" }.all { it.isMark && it.isAnnotation })
        assertRoundTrip("[Am] | [F] | % %% |: :| :|: || / - N.C. NC x2")
    }

    @Test
    fun `beat dots are marks between the chords`() {
        val dotted = line("[G] . . . | [C] . . . x2")

        assertTrue(dotted.text.isBlank())
        assertEquals(listOf("G", ".", ".", ".", "|", "C", ".", ".", ".", "x2"), dotted.chords.map { it.name })
        assertTrue(dotted.chords.filter { it.name != "G" && it.name != "C" }.all { it.isMark && it.isAnnotation })
        assertRoundTrip("[G] . . . | [C] . . . x2")

        val ellipses = line("[G] ... [C] \u2026")

        assertTrue(ellipses.text.isBlank())
        assertEquals(listOf("G", "...", "C", "\u2026"), ellipses.chords.map { it.name })
        assertTrue(ellipses.chords.filter { it.name != "G" && it.name != "C" }.all { it.isMark && it.isAnnotation })
        assertRoundTrip("[G] ... [C] \u2026")
    }

    @Test
    fun `a lone chord over dots or an ellipsis is a chord row`() {
        val dots = line("[Am]. . .")

        assertTrue(dots.text.isBlank())
        assertEquals(listOf(chord(0, "Am"), mark(0, "."), mark(2, "."), mark(4, ".")), dots.chords)
        assertRoundTrip("[Am]. . .")
        assertEquals(listOf(chord(0, "G"), mark(0, "\u2026")), line("[G]\u2026").chords)
        assertRoundTrip("[G]\u2026")
    }

    @Test
    fun `a chord row the converter writes from a pasted chart is one row`() {
        val line = line("[am] [H7] | % x2 N.C.")

        assertTrue(line.text.isBlank())
        assertEquals(listOf("am", "B7", "|", "%", "x2", "N.C."), line.chords.map { it.name })
    }

    @Test
    fun `a mark sharing its offset with a chord comes after it`() {
        assertEquals(listOf(chord(0, "D"), mark(0, "x4")), line("[D]x4").chords)
        assertRoundTrip("[D]x4")
    }

    @Test
    fun `a mark before the chords is the first thing on the row`() {
        assertEquals(listOf(mark(0, "x2"), chord(3, "G"), chord(4, "C")), line("x2 [G] [C]").chords)
        assertRoundTrip("x2 [G] [C]")
    }

    @Test
    fun `non-breaking spaces separate marks as spaces do`() {
        val line = line("[G] [C] x4")

        assertEquals(listOf(chord(0, "G"), chord(1, "C"), mark(2, "x4")), line.chords)
        assertTrue(line.text.isBlank())
    }

    @Test
    fun `a formatted mark on a chord row loses its formatting`() {
        val line = line("[G] [C] <i>x4</i>")

        assertEquals(listOf(chord(0, "G"), chord(1, "C"), mark(2, "x4")), line.chords)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `a row of annotations is a chord row too`() {
        assertEquals(
            listOf(Chord(position = 0, name = "softly", isAnnotation = true), mark(1, "x2")),
            line("[*softly] x2").chords,
        )
    }

    @Test
    fun `a sung line, a lone count, a backing line, and a word with a chord inside keep their text`() {
        listOf(
            "[G]Row, row, [C]row x2",
            "x4",
            "[G] [C] (oh yeah)",
            "[G] [C] x4 la",
            "[G]x[C]4",
            "[G]Hello . . .",
            "[G]Oh... [C]yeah...",
            "[G] [C] (. . .)",
            "[G]...[C]...",
            "[G] .... [C] ..",
            ". . .",
        ).forEach { text ->
            assertTrue(line(text).chords.none { it.isMark }, text)
            assertTrue(line(text).text.isNotBlank(), text)
        }
    }

    @Test
    fun `a count of any length is a mark`() {
        assertEquals(listOf(chord(0, "G"), mark(1, "x2000")), line("[G] x2000").chords)
    }

    @Test
    fun `a recalled chorus keeps its marks`() {
        val recall = ChordProParser.parse("{soc}\n[G] [C] x2\n{eoc}\n\n{chorus}").blocks.filterIsInstance<ChordProBlock.ChorusRecall>().single()
        val section = recall.blocks.single() as ChordProBlock.Section

        assertEquals(listOf(chord(0, "G"), chord(1, "C"), mark(2, "x2")), (section.lines.single() as ChordProLine.Lyrics).chords)
    }
}
