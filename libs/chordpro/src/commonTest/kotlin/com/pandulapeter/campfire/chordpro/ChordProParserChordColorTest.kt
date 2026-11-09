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

class ChordProParserChordColorTest {

    private fun chordColors(text: String) = ChordProParser.parse(text).blocks
        .filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }
        .filterIsInstance<ChordProLine.Lyrics>()
        .map { line -> line.chords.map { it.color } }

    @Test
    fun `chords take the colour of the chordcolour before them until another one changes it`() {
        val text = "[C]one\n{chordcolour: red}\n[D]two\n{start_of_chorus}\n[E]three\n{end_of_chorus}\n{chordcolour: #00f}\n[F]four\n{chordcolour}\n[G]five"

        assertEquals(listOf(listOf(null), listOf(0xFF0000), listOf(0xFF0000), listOf(0x0000FF), listOf(null)), chordColors(text))
    }

    @Test
    fun `an annotation and a mark keep the reader's colour`() {
        assertEquals(listOf(listOf(null, 0x008000, null)), chordColors("{chordcolour: green}\n[*softly] [G] x2"))
    }

    @Test
    fun `a chordcolour does not cut the section it stands in`() {
        val sections = ChordProParser.parse("{start_of_verse}\n[C]one\n{chordcolour: red}\n[D]two\n{end_of_verse}").blocks

        assertEquals(1, sections.size)
    }

    @Test
    fun `a colour that cannot be read gives the chords the reader's colour`() {
        assertEquals(listOf(listOf(null)), chordColors("{chordcolour: red}\n{chordcolour: not a colour}\n[C]one"))
    }

    @Test
    fun `chord colours survive serializing`() {
        val parsed = ChordProParser.parse("[C]one\n{chordcolour: red}\n[D]two\n\n[*x] words\n{chordcolour}\n[E]three\n{chordcolour: #123456}\n[F]four")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }
}
