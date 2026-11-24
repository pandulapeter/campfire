/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import kotlin.test.Test
import kotlin.test.assertEquals

class MetronomePanelRowTest {

    @Test
    fun `the beats stand in the middle of the window when it has room for them`() {
        // The window runs from -16 to 1000 + 72 + 16 = 1088 in the room's coordinates, its middle at 536.
        assertEquals(336, beatRowStart(room = 1000, beatsWidth = 400, trailingWidth = 72, startPadding = 16, endPadding = 16))
    }

    @Test
    fun `the beats keep clear of the button where the middle would reach it`() {
        assertEquals(100, beatRowStart(room = 500, beatsWidth = 400, trailingWidth = 300, startPadding = 16, endPadding = 16))
    }

    @Test
    fun `beats as wide as the room start at its start`() {
        assertEquals(0, beatRowStart(room = 400, beatsWidth = 400, trailingWidth = 72, startPadding = 16, endPadding = 16))
    }
}
