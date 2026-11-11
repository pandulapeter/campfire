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

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class TooltipPositionTest {

    private val safeBounds = IntRect(left = 20, top = 50, right = 380, bottom = 700)
    private val popupSize = IntSize(width = 100, height = 30)

    private fun positionOver(anchorBounds: IntRect) = tooltipPosition(
        anchorBounds = anchorBounds,
        safeBounds = safeBounds,
        popupSize = popupSize,
        spacing = 4,
    )

    @Test
    fun `a tooltip with room above its anchor is centred over it`() {
        assertEquals(IntOffset(x = 150, y = 166), positionOver(IntRect(left = 180, top = 200, right = 220, bottom = 240)))
    }

    @Test
    fun `a tooltip that would reach under the top inset goes below its anchor`() {
        assertEquals(IntOffset(x = 150, y = 104), positionOver(IntRect(left = 180, top = 60, right = 220, bottom = 100)))
    }

    @Test
    fun `a tooltip stays clear of the side insets`() {
        assertEquals(IntOffset(x = 20, y = 166), positionOver(IntRect(left = 0, top = 200, right = 40, bottom = 240)))
        assertEquals(IntOffset(x = 280, y = 166), positionOver(IntRect(left = 360, top = 200, right = 400, bottom = 240)))
    }

    @Test
    fun `a tooltip below an anchor at the bottom stays clear of the bottom inset`() {
        assertEquals(IntOffset(x = 150, y = 670), positionOver(IntRect(left = 180, top = 20, right = 220, bottom = 690)))
    }

    @Test
    fun `a tooltip wider than the safe area keeps to its start`() {
        val position = tooltipPosition(
            anchorBounds = IntRect(left = 180, top = 200, right = 220, bottom = 240),
            safeBounds = safeBounds,
            popupSize = IntSize(width = 500, height = 30),
            spacing = 4,
        )
        assertEquals(20, position.x)
    }
}
