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
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProParserTextColorTest {

    private fun lyricsLines(text: String) = ChordProParser.parse(text).blocks
        .filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }
        .filterIsInstance<ChordProLine.Lyrics>()

    private fun textColors(text: String) = lyricsLines(text).map { it.color }

    @Test
    fun `lyrics take the colour of the textcolour before them until another one changes it`() {
        val text = "one\n{textcolour: red}\n[D]two\n{start_of_chorus}\nthree\n{end_of_chorus}\n{textcolour: #00f}\nfour\n{textcolour}\nfive"

        assertEquals(listOf(null, 0xFF0000, 0xFF0000, 0x0000FF, null), textColors(text))
    }

    @Test
    fun `a textcolour leaves the chords their own colour`() {
        val line = lyricsLines("{chordcolour: green}\n{textcolour: red}\n[G]one").single()

        assertEquals(0xFF0000, line.color)
        assertEquals(0x008000, line.chords.single().color)
    }

    @Test
    fun `a textcolour does not cut the section it stands in`() {
        val sections = ChordProParser.parse("{start_of_verse}\none\n{textcolour: red}\ntwo\n{end_of_verse}").blocks

        assertEquals(1, sections.size)
    }

    @Test
    fun `a colour that cannot be read gives the lyrics the reader's colour`() {
        assertEquals(listOf(null), textColors("{textcolour: red}\n{textcolour: not a colour}\none"))
    }

    @Test
    fun `text colours survive serializing`() {
        val parsed = ChordProParser.parse("one\n{textcolour: red}\n[D]two\n\nthree\n{textcolour}\nfour\n{textcolour: #123456}\n{chordcolour: blue}\n[F]five")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }
}
