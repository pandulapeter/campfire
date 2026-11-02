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
    fun `new_song inside a delegated environment is part of it`() {
        listOf("abc", "ly", "svg", "textblock").forEach { environment ->
            val text = "{title: A}\n{start_of_$environment}\nx\n{ns}\n{new_song}\ny\n{end_of_$environment}"

            assertEquals(listOf(text), ChordProSplitter.split(text), environment)
        }
    }

    @Test
    fun `new_song after a delegated environment still splits the file`() {
        assertEquals(
            listOf("{title: A}\n{start_of_textblock}\nx\n{end_of_textblock}", "{title: B}"),
            ChordProSplitter.split("{title: A}\n{start_of_textblock}\nx\n{end_of_textblock}\n{ns}\n{title: B}"),
        )
    }

    @Test
    fun `an unclosed delegated environment takes the rest of the file`() {
        val text = "{title: A}\n{start_of_ly}\nx\n{ns}\n{title: B}"

        assertEquals(listOf(text), ChordProSplitter.split(text))
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

    @Test
    fun `a German chart is the same song as its standard copy`() {
        assertEquals(ChordProSplitter.comparable("{title: T}\n[H7]a [B]b"), ChordProSplitter.comparable("{title: T}\n[B7]a [Bb]b"))
    }

    @Test
    fun `any end directive closes a delegated environment, as the parser reads it`() {
        assertEquals(
            listOf("{start_of_abc}\nX\n{end_of_verse}", "{title: B}\n[C]b"),
            ChordProSplitter.split("{start_of_abc}\nX\n{end_of_verse}\n{ns}\n{title: B}\n[C]b"),
        )
    }

    @Test
    fun `a start directive with a value inside a delegated environment moves it on, as the parser reads it`() {
        assertEquals(
            listOf("{start_of_abc}\nX\n{start_of_verse: V}\na", "{title: B}\n[C]b"),
            ChordProSplitter.split("{start_of_abc}\nX\n{start_of_verse: V}\na\n{ns}\n{title: B}\n[C]b"),
        )
        // Another delegated environment started inside one keeps the `{ns}` part of it.
        assertEquals(1, ChordProSplitter.split("{start_of_abc}\n{start_of_ly: L}\n{ns}\nx").size)
    }

    @Test
    fun `a directive compares equal however it is spelled`() {
        val long = ChordProSplitter.comparable("{title: T}\n{start_of_chorus}\n[Am]a\n{end_of_chorus}\n{comment: Intro}")

        assertEquals(long, ChordProSplitter.comparable("{t:T}\n{soc}\n[Am]a\n{eoc}\n{c:Intro}"))
        assertEquals(long, ChordProSplitter.comparable("{Title T}\n{SOC}\n[Am]a\n{EOC}\n{comment:  Intro}"))
    }

    @Test
    fun `an empty value is no value`() {
        assertEquals(ChordProSplitter.comparable("{key}"), ChordProSplitter.comparable("{key:}"))
    }

    @Test
    fun `a selector is kept`() {
        assertNotEquals(ChordProSplitter.comparable("{title: T}"), ChordProSplitter.comparable("{title-guitar: T}"))
    }

    @Test
    fun `delegated text is left alone`() {
        val lilyPond = "{start_of_ly}\n{ c d e }\n{end_of_ly}"

        assertEquals(lilyPond, ChordProSplitter.comparable(lilyPond))
        assertNotEquals(ChordProSplitter.comparable(lilyPond), ChordProSplitter.comparable("{start_of_ly}\n{c: d e}\n{end_of_ly}"))
        assertEquals(ChordProSplitter.comparable("$lilyPond\n{comment: x}"), ChordProSplitter.comparable("$lilyPond\n{c:x}"))
    }

    @Test
    fun `different values still differ`() {
        assertNotEquals(ChordProSplitter.comparable("{title: B}"), ChordProSplitter.comparable("{t: A}"))
    }
}
