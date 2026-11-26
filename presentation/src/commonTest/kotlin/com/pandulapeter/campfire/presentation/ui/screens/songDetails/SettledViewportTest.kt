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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SettledViewportTest {

    @Test
    fun `the settled height keeps the previous one while the viewport moves`() {
        assertEquals(400.dp, settledSize(previous = 400.dp, live = 372.dp, isSettled = false))
    }

    @Test
    fun `the settled height takes the live one once the viewport is at rest`() {
        assertEquals(344.dp, settledSize(previous = 400.dp, live = 344.dp, isSettled = true))
    }

    @Test
    fun `the settled width keeps the previous one while the side panel slides in`() {
        assertEquals(640.dp, settledSize(previous = 640.dp, live = 520.dp, isSettled = false))
    }

    @Test
    fun `a page that follows the viewport takes the live height`() {
        assertEquals(372.dp, pageViewportHeight(live = 372.dp, settled = 400.dp, followsViewport = true))
    }

    @Test
    fun `a page beside it keeps the settled height`() {
        assertEquals(400.dp, pageViewportHeight(live = 372.dp, settled = 400.dp, followsViewport = false))
    }
}
