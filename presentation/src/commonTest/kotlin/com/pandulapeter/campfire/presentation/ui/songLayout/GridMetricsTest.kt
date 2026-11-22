/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import kotlin.test.Test
import kotlin.test.assertTrue

class GridMetricsTest {

    private val metrics = GridMetrics(em = 16f, textHeight = 20f)

    @Test
    fun `a repeat's dots stand clear of the heavy bar and inside the room left for them`() {
        assertTrue(metrics.repeatDotOffset - metrics.dotRadius > metrics.heavyWidth)
        assertTrue(metrics.repeatDotOffset + metrics.dotRadius < metrics.insetOf(GridEdge.REPEAT))
    }

    @Test
    fun `a repeat's dots stand inside the box's height`() = metrics.repeatDotCentres.forEach { y ->
        assertTrue(y - metrics.dotRadius > metrics.edgeInset && y + metrics.dotRadius < metrics.boxHeight - metrics.edgeInset)
    }

    @Test
    fun `a volta's bracket stands over its row`() {
        val top = 100f
        val bracketTop = metrics.voltaTopOf(top, metrics.strokeWidth)
        val bracketBottom = metrics.voltaBottomOf(top)
        assertTrue(bracketTop < bracketBottom && bracketBottom < top)
    }

    @Test
    fun `a held beat's line is thinner than a heavy edge`() = assertTrue(metrics.holdWidth < metrics.heavyWidth)
}
