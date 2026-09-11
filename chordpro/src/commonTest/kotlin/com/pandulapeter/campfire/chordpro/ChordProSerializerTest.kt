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
