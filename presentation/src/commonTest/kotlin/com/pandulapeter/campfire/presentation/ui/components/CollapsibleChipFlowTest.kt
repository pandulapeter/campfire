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

class CollapsibleChipFlowTest {

    @Test
    fun `chips flow onto the next line once the gap and the chip no longer fit`() {
        assertEquals(listOf(0, 0, 1, 1, 2), chipLines(widths = listOf(40, 50, 60, 30, 100), lineWidth = 100, gap = 10))
    }

    @Test
    fun `a chip wider than a line gets one of its own`() {
        assertEquals(listOf(0, 1, 2), chipLines(widths = listOf(20, 150, 20), lineWidth = 100, gap = 10))
    }

    @Test
    fun `no chips take no lines`() {
        assertEquals(0, chipLineCount(widths = emptyList(), lineWidth = 100, gap = 10))
    }

    @Test
    fun `a collapsed group shows the longest run from the start that fits`() {
        assertEquals(
            listOf(0, 1, 2, 3),
            collapsedChips(widths = List(10) { 45 }, isPinned = List(10) { false }, lineWidth = 100, gap = 10, maxLines = 2),
        )
    }

    @Test
    fun `a pinned chip past the fold is shown and takes the room of one from the run`() {
        assertEquals(
            listOf(0, 1, 2, 8),
            collapsedChips(
                widths = List(10) { 45 },
                isPinned = List(10) { it == 8 },
                lineWidth = 100,
                gap = 10,
                maxLines = 2,
            ),
        )
    }

    @Test
    fun `pinned chips are shown even where they alone take more lines than allowed`() {
        assertEquals(
            listOf(5, 6, 7),
            collapsedChips(widths = List(8) { 90 }, isPinned = List(8) { it >= 5 }, lineWidth = 100, gap = 10, maxLines = 2),
        )
    }

    @Test
    fun `groups that fit whole are not limited`() {
        assertEquals(listOf(null, null), chipBudgets(available = 500, natural = listOf(200, 300), minimum = listOf(100, 100)))
    }

    @Test
    fun `a short group is shown whole next to a long one`() {
        assertEquals(listOf(100, 300), chipBudgets(available = 400, natural = listOf(100, 600), minimum = listOf(100, 100)))
    }

    @Test
    fun `two long groups are cut down to about the same height`() {
        assertEquals(listOf(250, 250), chipBudgets(available = 500, natural = listOf(600, 800), minimum = listOf(100, 100)))
    }

    @Test
    fun `every group keeps its minimum where even those do not fit`() {
        assertEquals(listOf(100, 120), chipBudgets(available = 50, natural = listOf(600, 800), minimum = listOf(100, 120)))
    }
}
