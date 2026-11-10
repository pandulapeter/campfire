/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbcTokensTest {

    private fun String.tokenAt(column: Int) = AbcTokens.around(this, column)?.let { substring(it) }

    @Test
    fun `a token runs to the spaces and bar lines around it`() {
        assertEquals("C2D/0", "A2 C2D/0 E|F".tokenAt(6))
        assertEquals("F", "A2 C2D/0 E|F".tokenAt(11))
        assertEquals("|", "A2 C2D/0 E|F".tokenAt(10))
    }

    @Test
    fun `quotes, decorations, grace notes, and inline fields are taken whole`() {
        assertEquals("\"Am7\"", "C \"Am7\"D".tokenAt(3))
        assertEquals("!xyz!", "C !xyz!D".tokenAt(4))
        assertEquals("{end_of_abc}", "F|]{end_of_abc}".tokenAt(5))
        assertEquals("[Q:1/0]", "C [Q:1/0] D".tokenAt(5))
    }

    @Test
    fun `an unclosed quote runs to the end of the line`() {
        assertEquals("\"Am C D|", "\"Am C D|".tokenAt(1))
    }

    @Test
    fun `a column on a space or past the end is the token before it`() {
        assertEquals("C", "C   ".tokenAt(2))
        assertEquals("E", "C D E".tokenAt(9))
        assertNull("   ".tokenAt(1))
    }
}
