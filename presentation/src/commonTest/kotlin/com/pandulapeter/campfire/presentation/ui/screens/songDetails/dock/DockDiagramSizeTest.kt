/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DockDiagramSizeTest {

    @Test
    fun `a diagram is never drawn larger than the Chords section draws it`() {
        assertEquals(70.dp, dockDiagramSize(isFretted = true, maxHeight = 200.dp).height)
        assertEquals(40.dp, dockDiagramSize(isFretted = false, maxHeight = 200.dp).height)
    }

    @Test
    fun `a diagram short of room keeps its shape`() {
        val fretted = dockDiagramSize(isFretted = true, maxHeight = 35.dp)
        assertEquals(35.dp, fretted.height)
        assertEquals(28.dp, fretted.width)
    }

    @Test
    fun `the keyboard's diagram is wider than tall`() {
        val keyboard = dockDiagramSize(isFretted = false, maxHeight = 30.dp)
        assertTrue(keyboard.width > keyboard.height)
    }
}
