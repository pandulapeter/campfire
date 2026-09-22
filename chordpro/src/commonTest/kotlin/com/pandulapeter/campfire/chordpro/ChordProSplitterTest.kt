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
import kotlin.test.assertNotEquals

class ChordProSplitterTest {

    @Test
    fun `a file without new_song stays in one piece`() {
        val text = "{title: Only One}\n\n[Am]a"

        assertEquals(listOf(text), ChordProSplitter.split(text))
    }

    @Test
    fun `new_song splits the file and drops the directive line`() {
        val parts = ChordProSplitter.split("{title: First}\n\n[Am]a\n\n{new_song}\n\n{title: Second}\n\n[C]b")

        assertEquals(listOf("{title: First}\n\n[Am]a", "{title: Second}\n\n[C]b"), parts)
    }

    @Test
    fun `the short new_song form is recognised`() {
        assertEquals(listOf("{title: First}", "{title: Second}"), ChordProSplitter.split("{title: First}\n{ns}\n{title: Second}"))
    }

    @Test
    fun `parts without any content are dropped`() {
        assertEquals(listOf("{title: Only One}"), ChordProSplitter.split("{ns}\n\n{title: Only One}\n\n{ns}\n \n{ns}"))
    }

    @Test
    fun `a song compares equal whatever its line endings and the blank lines around it`() {
        val expected = ChordProSplitter.comparable("{title: T}\n\n[Am]a")

        assertEquals(expected, ChordProSplitter.comparable("{title: T}\n\n[Am]a\n"))
        assertEquals(expected, ChordProSplitter.comparable("\n{title: T}\n\n[Am]a\n\n"))
        assertEquals(expected, ChordProSplitter.comparable("{title: T}\r\n\r\n[Am]a\r\n"))
    }

    @Test
    fun `a part of a collection compares equal to the file it was exported as`() {
        val part = ChordProSplitter.split("{title: T}\n[Am]a\n\n{ns}\n{title: U}").first()

        assertEquals(ChordProSplitter.comparable("{title: T}\n[Am]a\n\n"), ChordProSplitter.comparable(part))
    }

    @Test
    fun `a byte order mark between two songs is not part of the second one`() {
        val parts = ChordProSplitter.split("{title: A}\nla\n{new_song}\n\uFEFF{title: B}\nlo\n")

        assertEquals(listOf("{title: A}\nla", "{title: B}\nlo"), parts)
        assertEquals("B", ChordProParser.parseMetadata(parts[1]).title)
    }

    @Test
    fun `a byte order mark at the start of a single song is dropped`() {
        assertEquals(listOf("{title: A}\nla"), ChordProSplitter.split("\uFEFF{title: A}\nla"))
    }

    @Test
    fun `a line holding nothing but a byte order mark is blank`() {
        assertEquals(listOf("{title: A}\nla", "{title: B}\nlo"), ChordProSplitter.split("{title: A}\nla\n{new_song}\n\uFEFF\n{title: B}\nlo"))
    }

    @Test
    fun `a song compares equal whether or not it carries a byte order mark`() {
        val expected = ChordProSplitter.comparable("{title: A}\nla")

        assertEquals(expected, ChordProSplitter.comparable("\uFEFF{title: A}\nla"))
        assertEquals(expected, ChordProSplitter.comparable("{title: A}\n\uFEFFla"))
    }

    @Test
    fun `a blank line inside a song still counts`() {
        assertNotEquals(ChordProSplitter.comparable("{title: T}\n[Am]a"), ChordProSplitter.comparable("{title: T}\n\n[Am]a"))
    }
}
