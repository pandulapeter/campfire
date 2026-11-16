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
import kotlin.test.assertEquals

class PopupRoomTest {

    @Test
    fun `a menu with room on either side of its anchor may take the whole room`() {
        assertEquals(600, anchoredMenuHeightPx(room = 600, roomAbove = 100, roomBelow = 900, margin = 48, minimum = 64))
    }

    @Test
    fun `a menu squeezed between its anchor and the keyboard takes no more than the larger side less the margin`() {
        assertEquals(238, anchoredMenuHeightPx(room = 660, roomAbove = 200, roomBelow = 286, margin = 48, minimum = 64))
        assertEquals(252, anchoredMenuHeightPx(room = 660, roomAbove = 300, roomBelow = 120, margin = 48, minimum = 64))
    }

    @Test
    fun `a menu with no room for one entry on either side keeps the whole room`() {
        assertEquals(400, anchoredMenuHeightPx(room = 400, roomAbove = 100, roomBelow = 90, margin = 48, minimum = 64))
    }
}
