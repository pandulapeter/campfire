/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordProColorsTest {

    @Test
    fun `a hexadecimal colour is read in its long and its short form`() {
        assertEquals(0x1A2B3C, ChordProColors.parse("#1a2B3c"))
        assertEquals(0xFF8800, ChordProColors.parse(" #f80 "))
    }

    @Test
    fun `a name is read whatever its case and spacing`() {
        assertEquals(0xFF0000, ChordProColors.parse("Red"))
        assertEquals(0x00008B, ChordProColors.parse("dark blue"))
    }

    @Test
    fun `an empty value, the foreground, and what is no colour name none`() {
        listOf(null, "", "foreground", "#12345", "#ggg", "reddish").forEach { assertNull(ChordProColors.parse(it), it) }
    }
}
