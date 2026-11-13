/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import kotlin.test.Test
import kotlin.test.assertEquals

class GridRowPositionsTest {

    @Test
    fun `a bar that does not fit starts a row after its opening bar line, and a bar too wide for any row starts its own`() {
        val positions = gridRowPositions(
            barWidths = listOf(30, 30, 30, 90, 10),
            barHeights = listOf(10, 10, 12, 10, 10),
            openingWidths = listOf(0, 5, 5, 5, 5),
            maxWidth = 70,
        )

        assertEquals(
            listOf(
                GridRowPositions.Bar(x = 0, y = 0, opensRow = true),
                GridRowPositions.Bar(x = 30, y = 0, opensRow = false),
                GridRowPositions.Bar(x = 5, y = 10, opensRow = true),
                GridRowPositions.Bar(x = 5, y = 22, opensRow = true),
                GridRowPositions.Bar(x = 5, y = 32, opensRow = true),
            ),
            positions.bars,
        )
        assertEquals(42, positions.height)
    }
}
