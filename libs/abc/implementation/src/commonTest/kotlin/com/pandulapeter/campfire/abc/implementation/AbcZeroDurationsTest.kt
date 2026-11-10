/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import kotlin.test.Test
import kotlin.test.assertEquals

class AbcZeroDurationsTest {

    @Test
    fun `every length divided by zero is found, of a note, a rest, a chord, and a grace note`() {
        val tune = "X:1\nK:C\nC/0 z//00 [CE]/0 {c/0}D|\n"
        assertEquals(listOf(2 to 1, 2 to 5, 2 to 14, 2 to 19), zeroDurationsIn(tune).map { it.line to it.column })
    }

    @Test
    fun `a length divided by a number with a zero in it is no division by zero`() {
        assertEquals(emptyList(), zeroDurationsIn("X:1\nK:C\nC/10 D/02 E0/2 F/|\n"))
    }

    @Test
    fun `fields, comments, quotes, decorations, inline fields, and text blocks hold no durations`() {
        val tune = "X:1\nL:1/0\nQ:1/0=100\nK:C\nw: 1/0\n% C/0\n\"C/0\"C !1/0!D [Q:1/0]E F % G/0\n%%begintext\nC/0\n%%endtext\n"
        assertEquals(emptyList(), zeroDurationsIn(tune))
    }
}
