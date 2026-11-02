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

class AbcTranspositionsTest {

    @Test
    fun `a tune's own transposition is the sum of its header's transpose lines`() {
        assertEquals(3, AbcTranspositions.of("X:1\n%%transpose 2\nI:transpose 1\nK:C\nC|"))
    }

    @Test
    fun `lines before X and after K are not counted`() {
        assertEquals(1, AbcTranspositions.of("%%transpose 5\nX:1\n%%transpose 1\nK:C\n%%transpose 4\nC|"))
    }

    @Test
    fun `a value is read as abc2svg reads it`() {
        assertEquals(2, AbcTranspositions.of("X:1\n%%transpose +2\nK:C"))
        assertEquals(2, AbcTranspositions.of("X:1\n%%transpose 2#\nK:C"))
        assertEquals(-2, AbcTranspositions.of("X:1\n%%transpose -2b\nK:C"))
        assertEquals(2, AbcTranspositions.of("X:1\n%%transpose 2 % up a tone\nK:C"))
        assertEquals(0, AbcTranspositions.of("X:1\n%%transpose M2\nK:C"))
        assertEquals(0, AbcTranspositions.of("X:1\n%%transpose 37\nK:C"))
    }

    @Test
    fun `a tune without X moves by nothing`() {
        assertEquals(0, AbcTranspositions.of("%%transpose 2\nK:C\nCDEF|"))
    }
}
