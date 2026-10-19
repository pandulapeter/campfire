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

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChordSheetLyricsTest {

    @Test
    fun `brackets, a line in braces, and a leading hash are escaped as the converter escapes prose`() {
        val escaped = ChordSheetLyrics.escaped("Sing [loud] now\n{title: Not a title}\n# not a comment\nPlain line").lines()

        assertEquals(4, escaped.size)
        assertFalse('[' in escaped[0], escaped[0])
        assertNull(ChordProParser.parseMetadata(escaped.joinToString("\n")).title)
        assertFalse(escaped[2].startsWith("#"), escaped[2])
        assertEquals("Plain line", escaped[3])
    }
}
