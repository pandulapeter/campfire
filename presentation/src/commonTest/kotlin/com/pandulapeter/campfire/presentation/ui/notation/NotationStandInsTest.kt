/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import kotlin.test.Test
import kotlin.test.assertEquals

class NotationStandInsTest {

    @Test
    fun `an edited tune stands in as the staff it replaced`() {
        assertEquals(mapOf("b2" to "b"), notationStandInsOf(previous = listOf("a", "b", "c"), current = listOf("a", "b2", "c")))
    }

    @Test
    fun `a tune added beside the others stands in for none`() {
        assertEquals(emptyMap(), notationStandInsOf(previous = listOf("a", "b"), current = listOf("a", "x", "b")))
    }

    @Test
    fun `a removed tune leaves the others their own`() {
        assertEquals(emptyMap(), notationStandInsOf(previous = listOf("a", "b", "c"), current = listOf("a", "c")))
    }

    @Test
    fun `several tunes edited at once pair in order`() {
        assertEquals(mapOf("a2" to "a", "b2" to "b"), notationStandInsOf(previous = listOf("a", "b"), current = listOf("a2", "b2")))
    }

    @Test
    fun `an unchanged song needs no stand-in`() {
        assertEquals(emptyMap(), notationStandInsOf(previous = listOf("a", "b"), current = listOf("a", "b")))
    }
}
