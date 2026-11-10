/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProAbcTunesTest {

    @Test
    fun `a tune is the lines of its block, cut at a blank line or a directive`() {
        val text = "{title: T}\n{start_of_abc}\nX:1\nK:C\n\nX:2\n{c: between}\nK:D\n{end_of_abc}\nLyrics\n{start_of_abc}\nX:3\n{end_of_abc}\n"
        assertEquals(listOf("X:1\nK:C", "X:2", "K:D", "X:3"), ChordProAbcTunes.of(text).map { it.text })
    }

    @Test
    fun `a position in a tune is the range of its token in the song`() {
        val text = "{title: T}\r\n{start_of_abc}\r\nX:1\r\nK:C\r\nC D E F|]{end_of_abc}\r\n"
        val tune = ChordProAbcTunes.of(text).single()
        assertEquals("{end_of_abc}", text.substring(tune.rangeAt(line = 2, column = 11)!!))
    }

    @Test
    fun `a position without a column is the whole line, and one past a line or the tune the token before it`() {
        val text = "{start_of_abc}\nX:1\n  K:Hbogus  \nC D|\n{end_of_abc}"
        val tune = ChordProAbcTunes.of(text).single()
        assertEquals("K:Hbogus", text.substring(tune.rangeAt(line = 1, column = null)!!))
        assertEquals("|", text.substring(tune.rangeAt(line = 2, column = 4)!!))
        assertEquals("K:Hbogus", text.substring(tune.rangeAt(line = 2, column = -1)!!))
        assertEquals("|", text.substring(tune.rangeAt(line = 7, column = 0)!!))
    }

    @Test
    fun `a song without a tune has none`() {
        assertEquals(emptyList(), ChordProAbcTunes.of("{title: T}\n[C]Lyrics\n{start_of_abc}\n\n{end_of_abc}").map { it.text })
    }
}
