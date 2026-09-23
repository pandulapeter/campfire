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

import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProSerializerTest {

    @Test
    fun `serializing a parsed song and parsing it again yields the same model`() {
        val parsed = ChordProParser.parse(EVERY_BLOCK_TYPE)

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    @Test
    fun `a legacy heading kept as a comment survives serializing`() {
        listOf("{sov}\n[Am]la\n{eov}\n{c: Chorus x2}\n{soc}\n[C]lo\n{eoc}", "{c: Chorus x2}\n\n[C]lo").forEach { text ->
            val parsed = ChordProParser.parse(text)

            assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
        }
    }

    @Test
    fun `a chorus cut by a comment comes back as one environment`() {
        val parsed = ChordProParser.parse("{soc}\n[C]one\n{comment: softly}\n[G]two\n{eoc}\n\n{chorus}")

        val serialized = ChordProSerializer.serialize(parsed)

        assertEquals("{start_of_chorus}\n[C]one\n{comment: softly}\n[G]two\n{end_of_chorus}\n\n{chorus}", serialized)
        assertEquals(parsed, ChordProParser.parse(serialized))
    }

    @Test
    fun `a legacy heading name cut into a tab comes back inside the tab`() {
        val parsed = ChordProParser.parse("{sot}\ne|---0---|\n{c: Solo}\ne|---3---|\n{eot}")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    @Test
    fun `an abc block survives serializing`() {
        val parsed = ChordProParser.parse("{start_of_abc}\nX:1\n[CEG]2 [A2B] |\n{end_of_abc}")

        assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
    }

    @Test
    fun `a tab with a blank line in it comes back as one section`() {
        val parsed = ChordProParser.parse("{sot: Riff}\ne|--0--|\n\ne|--3--|\n{eot}")
        val serialized = ChordProSerializer.serialize(parsed)

        assertEquals("{start_of_tab: Riff}\ne|--0--|\n\ne|--3--|\n{end_of_tab}", serialized)
        assertEquals(parsed, ChordProParser.parse(serialized))
    }

    @Test
    fun `metadata is written in canonical order and zero transposition is omitted`() {
        val song = ChordProParser.parse("{artist: A}\n{title: T}\n{transpose: 0}\n{meta: tuning DADGAD}")

        assertEquals("{title: T}\n{artist: A}\n{meta: tuning DADGAD}", ChordProSerializer.serialize(song))
    }

    @Test
    fun `chords and annotations are written back at their positions`() {
        val song = ChordProParser.parse("[Am]one [*hold]two [C]three")

        assertEquals("[Am]one [*hold]two [C]three", ChordProSerializer.serialize(song))
    }

    companion object {
        private val EVERY_BLOCK_TYPE = """
            # A source comment that the parser drops.
            {title: Fixture}
            {subtitle: Every block type}
            {artist: Campfire}
            {key: Am}
            {capo: 2}
            {tempo: 96}
            {meta: tuning DADGAD}

            {comment: Play softly}
            {comment_italic: An italic remark}
            {comment_box: A boxed remark}

            {start_of_verse: Verse 1}
            [Am]one two [C]three
            [*hold]four five
            {comment: Split by a comment}
            [F]six seven
            {end_of_verse}

            {start_of_chorus}
            [F]eight [G]nine
            {end_of_chorus}

            {start_of_bridge: The Bridge}
            [Dm]ten
            {end_of_bridge}

            {start_of_tab: Riff}
            e|-------0-------|
            B|-----1---1-----|
            {end_of_tab}

            {start_of_grid: Groove}
            | Am . . . | C . . . | (twice)
            {end_of_grid}

            {start_of_solo: Guitar solo}
            [Am] [F] [C] [G]
            {start_of_tab}
            e|-------5-------|
            {end_of_tab}
            back to [C]lyrics
            {end_of_solo}

            {chorus}

            {column_break}

            eleven twelve outside any environment
            [Am]thirteen [G]fourteen

            fifteen sixteen
        """.trimIndent()
    }
}
