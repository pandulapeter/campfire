/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSerializer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting.Style
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProMarkupTest {

    @Test
    fun `bold, italic, and underline tags format the text they enclose`() {
        val line = lyrics("a <b>bold</b>, <I>italic</I>, <strong><em>both</em></strong> and <u>under</u>")

        assertEquals("a bold, italic, both and under", line.text)
        assertEquals(
            listOf(
                Formatting(2, 6, Style.BOLD),
                Formatting(8, 14, Style.ITALIC),
                Formatting(16, 20, Style.BOLD),
                Formatting(16, 20, Style.ITALIC),
                Formatting(25, 30, Style.UNDERLINE),
            ),
            line.formatting,
        )
    }

    @Test
    fun `chords stay on the characters they were written at`() {
        val line = lyrics("[C]There <b>[Am]is a</b> [F]house")

        assertEquals("There is a house", line.text)
        assertEquals(listOf(0, 6, 11), line.chords.map { it.position })
        assertEquals(listOf(Formatting(6, 10, Style.BOLD)), line.formatting)
    }

    @Test
    fun `a tag left open ends with the line and a stray closing tag is dropped`() {
        val (line, next) = lines("one</i> <u>two\nthree")

        assertEquals("one two", line.text)
        assertEquals(listOf(Formatting(4, 7, Style.UNDERLINE)), line.formatting)
        assertEquals(emptyList(), next.formatting)
    }

    @Test
    fun `other text in angle brackets stays lyrics`() {
        val line = lyrics("<3 <intro> a<b c> <br> <b>")

        assertEquals("<3 <intro> a<b c> <br> ", line.text)
        assertEquals(emptyList(), line.formatting)
    }

    @Test
    fun `adjacent ranges of one style are one range`() {
        assertEquals(listOf(Formatting(0, 2, Style.BOLD)), lyrics("<b>a</b><b>b</b>").formatting)
    }

    @Test
    fun `a tag inside a chord's brackets is part of the chord name`() {
        assertEquals("<b>Am</b>", lyrics("[<b>Am</b>]la").chords.single().name)
    }

    @Test
    fun `formatted lines survive serializing`() {
        val parsed = ChordProParser.parse("<b>[C]Bold</b> plain [G]<i>it<u>al</i>ic</u>[D]\n[Am]<b></b>x")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    private fun lyrics(line: String) = lines(line).single()

    private fun lines(text: String) = ChordProParser.parse(text).blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>()
}
