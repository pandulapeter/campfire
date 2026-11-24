/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ChipIntrinsicsCacheTest {

    @Test
    fun `both intrinsic heights of one measure share a group's walk`() {
        val cache = ChipIntrinsicsCache()
        var widths = listOf(40, 60)
        val (natural, minimum) = cache.during {
            val first = cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(widths, LINE_HEIGHT) }
            widths = listOf(41, 60)
            first to cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(widths, LINE_HEIGHT) }
        }
        assertEquals(natural, minimum)
    }

    @Test
    fun `a count that widens a chip is seen by the next measure`() {
        val cache = ChipIntrinsicsCache()
        val before = cache.during { cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(listOf(40, 60), LINE_HEIGHT) } }
        val after = cache.during { cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(listOf(41, 60), LINE_HEIGHT) } }
        assertNotEquals(before, after)
    }

    @Test
    fun `nothing is cached outside a measure`() {
        val cache = ChipIntrinsicsCache()
        cache.during { cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(listOf(40), LINE_HEIGHT) } }
        assertEquals(ChipWalk(listOf(41), LINE_HEIGHT), cache.walkOf(group = GROUP, lineWidth = LINE_WIDTH) { ChipWalk(listOf(41), LINE_HEIGHT) })
    }

    @Test
    fun `cached widths share the height out as fresh ones do`() {
        val cache = ChipIntrinsicsCache()
        val walks = listOf(ChipWalk(listOf(40, 60, 80), LINE_HEIGHT), ChipWalk(listOf(120, 30), LINE_HEIGHT))
        val budgets = cache.during {
            val cached = walks.mapIndexed { index, walk -> cache.walkOf(group = index, lineWidth = LINE_WIDTH) { walk } }
            chipBudgets(available = 50, natural = cached.map { it.lineHeight * it.widths.size }, minimum = cached.map { it.lineHeight })
        }
        assertEquals(chipBudgets(available = 50, natural = walks.map { it.lineHeight * it.widths.size }, minimum = walks.map { it.lineHeight }), budgets)
    }
}

private const val GROUP = "tags"
private const val LINE_WIDTH = 200
private const val LINE_HEIGHT = 32
