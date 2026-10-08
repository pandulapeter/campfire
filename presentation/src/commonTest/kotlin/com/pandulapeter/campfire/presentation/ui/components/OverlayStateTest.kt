/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayStateTest {

    @Test
    fun `a menu stays counted until every opening is closed`() {
        val state = OverlayState()
        assertFalse(state.isAnyMenuOpen)
        state.onMenuOpened()
        state.onMenuOpened()
        state.onMenuClosed()
        assertTrue(state.isAnyMenuOpen)
        state.onMenuClosed()
        assertFalse(state.isAnyMenuOpen)
    }

    @Test
    fun `two states count apart`() {
        val first = OverlayState()
        val second = OverlayState()
        first.onMenuOpened()
        assertTrue(first.isAnyMenuOpen)
        assertFalse(second.isAnyMenuOpen)
    }
}
