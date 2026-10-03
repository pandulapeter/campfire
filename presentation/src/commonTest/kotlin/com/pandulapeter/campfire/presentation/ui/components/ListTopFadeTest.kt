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

class ListTopFadeTest {

    @Test
    fun unscrolledListHasNoFadeWithExpandedOrCollapsedHeaders() {
        assertEquals(0f, strength(canScrollBackward = false, index = 0, offset = 0, headerHeight = 56f))
        assertEquals(0f, strength(canScrollBackward = false, index = 1, offset = 0, headerHeight = 0f))
        // A collapsed slot can leave the first card at index 1 even before any real pixels have been scrolled.
        assertEquals(0f, strength(canScrollBackward = true, index = 1, offset = 0, headerHeight = 0f))
    }

    @Test
    fun collapsedHeaderDoesNotSkipTheGradualFade() {
        for ((offset, expected) in listOf(1 to 1f / 24f, 6 to 0.25f, 12 to 0.5f, 24 to 1f, 48 to 1f)) {
            assertEquals(expected, strength(canScrollBackward = true, index = 1, offset = offset, headerHeight = 0f))
        }
    }

    @Test
    fun expandedHeaderScrollStartsGraduallyAndReachesFullStrengthBeforeTheFirstCard() {
        assertEquals(0.25f, strength(canScrollBackward = true, index = 0, offset = 6, headerHeight = 56f))
        assertEquals(1f, strength(canScrollBackward = true, index = 1, offset = 0, headerHeight = 56f))
        assertEquals(1f, strength(canScrollBackward = true, index = 2, offset = 0, headerHeight = 0f))
    }

    private fun strength(canScrollBackward: Boolean, index: Int, offset: Int, headerHeight: Float) = listTopFadeStrength(
        canScrollBackward = canScrollBackward,
        firstVisibleItemIndex = index,
        scrollOffset = offset,
        firstCardIndex = 1,
        coveredHeightPx = headerHeight,
        fadeHeightPx = 24f,
    )
}
