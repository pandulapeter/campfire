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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettledViewportTest {

    @Test
    fun `an app bar fully expanded, barely moved or fully collapsed is settled`() {
        assertTrue(isAppBarSettled(0f))
        assertTrue(isAppBarSettled(0.005f))
        assertTrue(isAppBarSettled(1f))
    }

    @Test
    fun `an app bar anywhere between is not settled`() {
        assertFalse(isAppBarSettled(0.01f))
        assertFalse(isAppBarSettled(0.5f))
        assertFalse(isAppBarSettled(0.999f))
    }

    @Test
    fun `the settled height keeps the previous one while the viewport moves`() {
        assertEquals(400.dp, settledHeight(previous = 400.dp, live = 372.dp, isSettled = false))
    }

    @Test
    fun `the settled height takes the live one once the viewport is at rest`() {
        assertEquals(344.dp, settledHeight(previous = 400.dp, live = 344.dp, isSettled = true))
    }
}
