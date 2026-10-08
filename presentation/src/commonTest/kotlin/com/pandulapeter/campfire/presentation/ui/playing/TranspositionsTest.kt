/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import kotlin.test.Test
import kotlin.test.assertEquals

class TranspositionsTest {

    @Test
    fun `a transposition wraps around the octave, halfway being up`() {
        assertEquals(6, wrapTransposition(6))
        assertEquals(6, wrapTransposition(-6))
        assertEquals(-5, wrapTransposition(7))
        assertEquals(-5, wrapTransposition(-5))
        assertEquals(1, wrapTransposition(-11))
        assertEquals(0, wrapTransposition(12))
        assertEquals(0, wrapTransposition(0))
    }
}
