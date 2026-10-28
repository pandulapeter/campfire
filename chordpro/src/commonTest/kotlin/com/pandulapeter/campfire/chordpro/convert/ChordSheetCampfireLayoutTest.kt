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

    private companion object {
        const val MARGIN = 40.0
        const val GLYPH = 6.0
        const val BODY = 12.0
        const val LABEL = 11.0
    }
}
