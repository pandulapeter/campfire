/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordSheetCampfireLayoutTest {

    /** A line as the PDF reader hands it over: a span a glyph, [width] apart, from [x]. */
    private fun text(
        value: String,
        x: Double = MARGIN,
        size: Double = BODY,
        bold: Boolean = false,
        italic: Boolean = false,
        mono: Boolean = false,
        width: Double = GLYPH,
    ) = ChordSheet.Line(value.mapIndexed { index, character ->
        ChordSheet.Span(character.toString(), x + index * width, x + (index + 1) * width, size, isBold = bold, isMonospace = mono, isItalic = italic)
    })

    /** A row of chords over a lyric line, each chord at the column of the character it is set on. */
    private fun chords(vararg chords: Pair<String, Int>) = ChordSheet.Line(chords.flatMap { (name, column) ->
        text(name, x = MARGIN + column * GLYPH, bold = true).spans
    })

    private fun convert(vararg lines: ChordSheet.Line, isCampfireExport: Boolean = true) =
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(text("A song", size = 19.0, bold = true)) + lines)), isCampfireExport = isCampfireExport)).single()

    @Test
    fun `labels open sections named by them, a label over a tab is the tab's, and grids and comments come back as they were written`() {
        assertEquals(
            """
            {title: A song}

            {start_of_tab: Picking pattern}
            {comment_box: Pick slowly.}
              Am
            e|---0---|
            C|---1---|
            {end_of_tab}

            {start_of_solo: Organ solo}
            {start_of_grid}
            | Am | C  |
            {end_of_grid}
            {end_of_solo}

            {start_of_verse: Verse 1}
            [Am]Hello world
            {end_of_verse}

            {comment: Let it ring.}

            """.trimIndent(),
            convert(
                text("Picking pattern", size = LABEL, bold = true),
                text("Pick slowly.", x = MARGIN + 5.0),
                text("  Am", mono = true),
                text("e|---0---|", mono = true),
                text("C|---1---|", mono = true),
                text("Organ solo", size = LABEL, bold = true),
                text("| Am | C  |", bold = true, mono = true),
                text("Verse 1", size = LABEL, bold = true),
                chords("Am" to 0),
                text("Hello world"),
                text("Let it ring.", italic = true),
            ),
        )
    }

    @Test
    fun `the padding under a wide chord is left out and every chord stays on the character it is printed over`() {
        val padding = '\u00A0'
        assertEquals(
            """
            {title: A song}

            [Am]is a [C]house, [D]o[F]ver, [G]said[E7] and sai[A]d [B]
            """.trimIndent() + "\n",
            convert(
                chords("Am" to 0, "C" to 7, "D" to 14, "F" to 17, "G" to 22, "E7" to 26, "A" to 34, "B" to 36),
                text("is a $padding${padding}house, o$padding${padding}ver, said and said"),
            ),
        )
    }

    @Test
    fun `another producer's sheet is not read by campfire's layout`() {
        val lines = arrayOf(text("Organ solo", size = LABEL, bold = true), text("| Am | C  |", bold = true, mono = true), chords("Am" to 0), text("Hello world"))
        assertTrue("{start_of_solo: Organ solo}" in convert(*lines))
        assertFalse("{start_of_solo" in convert(*lines, isCampfireExport = false))
    }

    /** A row of a right-to-left song as the reader hands it over: one span over its glyphs, ending [inset] from the right. */
    private fun rtl(value: String, inset: Double = 0.0, width: Double = value.length * GLYPH) = ChordSheet.Line(
        listOf(ChordSheet.Span(value, RIGHT - inset - width, RIGHT - inset, BODY)),
    )

    private fun boxes(vararg lines: ChordSheet.Line) = ChordSheetCampfireLayout.roles(lines.toList(), lines.map { false }, BODY).withIndex()
        .filter { it.value == ChordSheetCampfireLayout.Role.BOX_COMMENT }
        .map { it.index }

    @Test
    fun `a lyric of a song printed from the right is never a boxed comment by where it starts`() {
        assertEquals(
            emptyList(),
            boxes(
                rtl("שלום עולם ומלואו"),
                rtl("שלום עולם"),
                rtl("שלום עולם ומלואו וכו"),
                // Starts 5 points right of the line two above it, which in a song printed from the left would be a box.
                rtl("שלום עולם", width = 9 * GLYPH - 5.0),
                rtl("שלום"),
            ),
        )
    }

    @Test
    fun `a boxed comment of a song printed from the right is found by its right edge, in any letters`() {
        assertEquals(listOf(1), boxes(rtl("שלום עולם ומלואו"), rtl("לאט לאט", inset = 5.0), rtl("שלום עולם")))
        assertEquals(listOf(1), boxes(rtl("שלום עולם ומלואו"), rtl("x2", inset = 5.0), rtl("שלום עולם"), rtl("שלום")))
    }

    @Test
    fun `a chorus of a song printed from the right is no box, and a box inside it is one`() {
        assertEquals(
            listOf(3),
            boxes(rtl("שלום עולם ומלואו"), rtl("שלום עולם"), rtl("שלום עולם ומלואו", inset = 8.0), rtl("לאט לאט", inset = 13.0), rtl("שלום", inset = 8.0)),
        )
    }

    @Test
    fun `each song on a page is judged from its own side`() {
        assertEquals(
            listOf(1, 6),
            boxes(
                text("Hello world"),
                text("Pick slowly.", x = MARGIN + 5.0),
                text("Hello"),
                ChordSheet.Line(listOf(ChordSheet.Span("שיר", RIGHT - 3 * 19.0, RIGHT, 19.0, isBold = true))),
                rtl("שלום עולם ומלואו"),
                rtl("שלום עולם"),
                rtl("x2", inset = 5.0),
                rtl("שלום עולם", width = 9 * GLYPH - 5.0),
            ),
        )
    }

    /** [line] with its text [rows] body sizes under the page's first line, the way the export spaces its rows. */
    private fun at(rows: Double, line: ChordSheet.Line) = line.copy(baseline = TOP + rows * BODY)

    /** The roles of [lines], those holding only bold chords taken for chord rows, as the converter's test says. */
    private fun roles(vararg lines: ChordSheet.Line) = ChordSheetCampfireLayout.roles(
        lines.toList(),
        lines.map { line -> line.spans.any { it.text.isNotBlank() } && line.spans.filter { it.text.isNotBlank() }.all { it.isBold } },
        BODY,
    )

    @Test
    fun `an italic line right under its chords is a lyric, as are its wrapped rows, and an italic line a row under chords alone is a comment`() {
        val comment = ChordSheetCampfireLayout.Role.COMMENT
        assertEquals(
            listOf(null, null, null, null, comment, null, comment, null, comment),
            roles(
                at(0.0, chords("Am" to 0, "G" to 9)),
                at(1.3, text("Ooh ooh baby", italic = true)),
                at(2.75, text("and on", italic = true)),
                at(4.2, chords("Am" to 0, "G" to 3)),
                at(5.65, text("x2", italic = true)),
                at(7.1, text("Hello world")),
                at(8.55, text("Let it ring.", italic = true)),
                text(""),
                at(11.0, text("Somewhere unknown", italic = true)).copy(baseline = null),
            ),
        )
    }

    @Test
    fun `an italic line under chords at the top of the next column is a comment`() {
        assertEquals(ChordSheetCampfireLayout.Role.COMMENT, roles(at(30.0, chords("Am" to 0)), at(0.0, text("x2", italic = true)))[1])
    }

    @Test
    fun `an italic lyric under its chords and a comment under chords alone both come back from campfire's pdf`() {
        assertEquals(
            """
            {title: A song}

            [Am]Ooh ooh [G]baby
            [Am] [G]

            {comment: x2}
            """.trimIndent() + "\n",
            convert(
                at(0.0, chords("Am" to 0, "G" to 8)),
                at(1.3, text("Ooh ooh baby", italic = true)),
                at(2.75, chords("Am" to 0, "G" to 3)),
                at(4.2, text("x2", italic = true)),
            ),
        )
    }

    private companion object {
        const val TOP = 100.0
        const val RIGHT = 400.0
        const val MARGIN = 40.0
        const val GLYPH = 6.0
        const val BODY = 12.0
        const val LABEL = 11.0
    }
}
