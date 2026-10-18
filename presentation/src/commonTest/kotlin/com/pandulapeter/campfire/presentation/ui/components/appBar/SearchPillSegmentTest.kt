/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPillSegmentTest {

    private val pill = Rect(left = 100f, top = 10f, right = 200f, bottom = 50f)
    private val centers = listOf(Offset(120f, 30f), Offset(156f, 30f), Offset(180f, 30f))

    private fun segment(index: Int, openProgress: Float = 0f) = searchPillSegment(
        closedPill = pill,
        centers = centers,
        index = index,
        containerRadius = 20f,
        openProgress = openProgress,
    )

    @Test
    fun `the closed pill is shared by its buttons edge to edge, meeting halfway between them`() {
        assertEquals(Rect(left = 100f, top = 10f, right = 138f, bottom = 50f), segment(0).bounds)
        assertEquals(Rect(left = 138f, top = 10f, right = 168f, bottom = 50f), segment(1).bounds)
        assertEquals(Rect(left = 168f, top = 10f, right = 200f, bottom = 50f), segment(2).bounds)
    }

    @Test
    fun `only the ends of the pill are rounded`() {
        assertEquals(SearchPillSegment(segment(0).bounds, leftRadius = 20f, rightRadius = 0f), segment(0))
        assertEquals(SearchPillSegment(segment(1).bounds, leftRadius = 0f, rightRadius = 0f), segment(1))
        assertEquals(SearchPillSegment(segment(2).bounds, leftRadius = 0f, rightRadius = 20f), segment(2))
    }

    @Test
    fun `a lone button fills the whole pill`() {
        val alone = searchPillSegment(closedPill = pill, centers = listOf(Offset(150f, 30f)), index = 0, containerRadius = 20f, openProgress = 0f)

        assertEquals(SearchPillSegment(pill, leftRadius = 20f, rightRadius = 20f), alone)
    }

    @Test
    fun `with the search open each button has its own circle`() {
        assertEquals(
            SearchPillSegment(Rect(left = 136f, top = 10f, right = 176f, bottom = 50f), leftRadius = 20f, rightRadius = 20f),
            segment(1, openProgress = 1f),
        )
    }
}
