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
}
