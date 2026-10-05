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

class BalancedRowsTest {

    private val four = listOf(100, 80, 120, 60)

    @Test
    fun `all items share one row where they fit`() {
        assertEquals(4, balancedColumnCount(four, maxWidth = 360 + 3 * 8, gap = 8))
    }

    @Test
    fun `four items that do not fit in one row go two and two, measured by the widest of each column`() {
        // The columns are 120 (the first and the third) and 80 (the second and the fourth).
        assertEquals(2, balancedColumnCount(four, maxWidth = 208, gap = 8))
        assertEquals(1, balancedColumnCount(four, maxWidth = 207, gap = 8))
    }

    @Test
    fun `three over one is never chosen, however much room three would have`() {
        assertEquals(1, balancedColumnCount(listOf(100, 100, 100, 300), maxWidth = 299, gap = 0))
    }

    @Test
    fun `an odd count is either one row or one item per row`() {
        val three = listOf(100, 100, 100)
        assertEquals(3, balancedColumnCount(three, maxWidth = 300, gap = 0))
        assertEquals(1, balancedColumnCount(three, maxWidth = 299, gap = 0))
    }

    @Test
    fun `a single item or none is one per row`() {
        assertEquals(1, balancedColumnCount(listOf(500), maxWidth = 100, gap = 8))
        assertEquals(1, balancedColumnCount(emptyList(), maxWidth = 100, gap = 8))
    }
}
