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

import kotlin.test.Test
import kotlin.test.assertEquals

internal class DockPanelGridTest {

    private fun xsOf(widths: List<Int>) = dockPanelGridOf(widths = widths, maxWidth = 140, gap = 8).lines.map { line -> line.map { it.x } }

    @Test
    fun `diagrams that fit two to a line make two columns`() {
        assertEquals(listOf(listOf(0, 72), listOf(0, 72)), xsOf(listOf(64, 64, 64, 64)))
    }

    @Test
    fun `an odd last diagram stays in the first column`() {
        assertEquals(listOf(listOf(0, 72), listOf(0)), xsOf(listOf(64, 64, 64)))
    }

    @Test
    fun `a diagram too wide to share its line is centred in the panel`() {
        assertEquals(listOf(listOf(0, 72), listOf(28), listOf(0, 72)), xsOf(listOf(64, 64, 80, 64, 64)))
    }

    @Test
    fun `a wide last diagram alone on its line is centred too`() {
        assertEquals(listOf(listOf(0, 72), listOf(28)), xsOf(listOf(64, 64, 80)))
    }

    @Test
    fun `a narrow diagram pushed onto a line of its own by a wide one is centred`() {
        assertEquals(listOf(listOf(0, 72), listOf(36), listOf(28)), xsOf(listOf(64, 64, 64, 80)))
    }
}
