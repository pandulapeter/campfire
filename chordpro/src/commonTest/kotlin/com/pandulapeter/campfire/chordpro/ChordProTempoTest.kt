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

class ChordProTempoTest {

    @Test
    fun `reads a plain number`() = assertEquals(120, ChordProTempo.parse("120"))

    @Test
    fun `reads the first number of what people write`() {
        assertEquals(120, ChordProTempo.parse("120 bpm"))
        assertEquals(96, ChordProTempo.parse("♩ = 96"))
    }

    @Test
    fun `rounds a fraction`() {
        assertEquals(98, ChordProTempo.parse("97.5"))
        assertEquals(97, ChordProTempo.parse("97,4"))
    }

    @Test
    fun `reads nothing without a positive number`() {
        assertNull(ChordProTempo.parse(null))
        assertNull(ChordProTempo.parse("fast"))
        assertNull(ChordProTempo.parse("0"))
    }
}
