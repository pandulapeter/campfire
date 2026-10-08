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
import kotlin.test.assertNull

class ChordProTimeTest {

    @Test
    fun `reads a fraction`() {
        assertEquals(3 to 4, ChordProTime.parse("3/4"))
        assertEquals(6 to 8, ChordProTime.parse(" 6 / 8 "))
    }

    @Test
    fun `reads the common and cut time marks`() {
        assertEquals(4 to 4, ChordProTime.parse("C"))
        assertEquals(2 to 2, ChordProTime.parse("C|"))
    }

    @Test
    fun `reads nothing out of range`() {
        assertNull(ChordProTime.parse("17/4"))
        assertNull(ChordProTime.parse("4/3"))
        assertNull(ChordProTime.parse("0/4"))
        assertNull(ChordProTime.parse("waltz"))
        assertNull(ChordProTime.parse(null))
    }
}
