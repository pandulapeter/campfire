/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranspositionLabelTest {

    @Test
    fun `a transposition up is signed and followed by the key it takes the song to`() {
        assertEquals("+2 $KEY_SEPARATOR C", transpositionLabel(2, "C"))
    }

    @Test
    fun `no transposition and no key is a bare zero`() {
        assertEquals("0", transpositionLabel(0, null))
    }

    @Test
    fun `a transposition down starts with its own sign and ends with the key`() {
        val label = transpositionLabel(-1, "Bb")
        assertTrue(label.startsWith("-1"))
        assertTrue(label.endsWith("Bb"))
    }

    @Test
    fun `a blank key is left out`() {
        assertEquals("+3", transpositionLabel(3, " "))
    }
}
