/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupedRowsTest {

    private val pill = listOf(150)
    private val chords = listOf(150, 110)
    private val click = listOf(220, 80)

    @Test
    fun `every group shares one line where they all fit`() {
        // 150 + 16 + (150 + 8 + 110) + 16 + (220 + 8 + 80)
        assertEquals(
            listOf(listOf(item(0, 0), item(1, 0), item(1, 1), item(2, 0), item(2, 1))),
            groupedLines(listOf(pill, chords, click), maxWidth = 758, gap = 8, groupGap = 16),
        )
    }

    @Test
    fun `a group that does not fit after the one before it starts a line of its own`() {
        assertEquals(
            listOf(listOf(item(0, 0), item(1, 0), item(1, 1)), listOf(item(2, 0), item(2, 1))),
            groupedLines(listOf(pill, chords, click), maxWidth = 757, gap = 8, groupGap = 16),
        )
        assertEquals(
            listOf(listOf(item(0, 0)), listOf(item(1, 0), item(1, 1)), listOf(item(2, 0), item(2, 1))),
            groupedLines(listOf(pill, chords, click), maxWidth = 400, gap = 8, groupGap = 16),
        )
    }

    @Test
    fun `a group too wide for any line flows its items, and the next group starts under it`() {
        // The second group would fit after the capo, but starts a line of its own.
        assertEquals(
            listOf(listOf(item(0, 0)), listOf(item(0, 1)), listOf(item(1, 0), item(1, 1))),
            groupedLines(listOf(listOf(300, 80), listOf(100, 100)), maxWidth = 308, gap = 8, groupGap = 16),
        )
    }

    @Test
    fun `a broken group keeps on one line what fits on it`() {
        assertEquals(
            listOf(listOf(item(0, 0), item(0, 1)), listOf(item(0, 2))),
            groupedLines(listOf(listOf(100, 100, 100)), maxWidth = 250, gap = 8, groupGap = 16),
        )
    }

    @Test
    fun `an empty group takes no room and no gap`() {
        assertEquals(
            listOf(listOf(item(1, 0), item(1, 1), item(3, 0))),
            groupedLines(listOf(emptyList(), chords, emptyList(), listOf(40)), maxWidth = 150 + 8 + 110 + 16 + 40, gap = 8, groupGap = 16),
        )
        assertEquals(emptyList(), groupedLines(listOf(emptyList(), emptyList()), maxWidth = 100, gap = 8, groupGap = 16))
    }

    @Test
    fun `an item wider than the line is given a line of its own`() {
        assertEquals(
            listOf(listOf(item(0, 0)), listOf(item(1, 0))),
            groupedLines(listOf(listOf(500), listOf(50)), maxWidth = 300, gap = 8, groupGap = 16),
        )
    }

    private fun item(group: Int, index: Int) = GroupedItem(group, index)
}
