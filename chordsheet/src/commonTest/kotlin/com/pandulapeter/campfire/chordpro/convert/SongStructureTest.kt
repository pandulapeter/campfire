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

class SongStructureTest {

    @Test
    fun `a grid's shape printed over it is the grid's, not a section's label`() {
        fun line(text: String, bold: Boolean = false, size: Double = 12.0) =
            ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 7.0, (index + 1) * 7.0, size, isBold = bold) })
        val lines = listOf(
            line("Song", bold = true, size = 20.0),
            line("4x4", bold = true, size = 11.0),
            line("| A7 . . . | D7 . . . |", bold = true),
            line("| E7 . . . | A7 . . . |", bold = true),
            line("Verse", bold = true, size = 11.0),
            line("A7          D7", bold = true),
            line("Woke up this morning"),
        )
        assertEquals(
            "{title: Song}\n\n{start_of_grid: 4x4}\n| A7 . . . | D7 . . . |\n| E7 . . . | A7 . . . |\n{end_of_grid}\n" +
                "{start_of_verse: Verse}\n[A7]Woke up this [D7]morning\n{end_of_verse}\n",
            ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
        )
    }

    @Test
    fun `verses numbered at the start of their first line are verse sections under their numbers`() {
        val sheet = """
            |Am           C
            |1. Hello my old friend
            |G
            |Sing it again
            |
            |Am           C
            |2. The second verse is here
            |G
            |Sing it again
            |
            |In 1. place a line is sung
        """.trimMargin()
        assertEquals(
            "{start_of_verse: 1}\n[Am]Hello my [C]old friend\n[G]Sing it again\n{end_of_verse}\n\n" +
                "{start_of_verse: 2}\n[Am]The second [C]verse is here\n[G]Sing it again\n{end_of_verse}\n\nIn 1. place a line is sung\n",
            ChordSheetConverter.convert(ChordSheet.ofPlainText(sheet), guessesTitle = false).single(),
        )
    }
}
