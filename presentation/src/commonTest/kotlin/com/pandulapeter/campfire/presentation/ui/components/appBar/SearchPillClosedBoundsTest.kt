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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPillClosedBoundsTest {

    private val pill = SearchPill().apply {
        closedActions = Rect(left = 100f, top = 0f, right = 300f, bottom = 60f)
        closedSearchActionsWidth = 60f
    }

    @Test
    fun `a detached button's room is cut off the right of the pill read from the left`() = assertEquals(
        Rect(left = 100f, top = 10f, right = 240f, bottom = 50f),
        pill.closedBounds(height = 40f, detachProgress = 1f, layoutDirection = LayoutDirection.Ltr),
    )

    @Test
    fun `a detached button's room is cut off the left of the pill read from the right`() = assertEquals(
        Rect(left = 160f, top = 10f, right = 300f, bottom = 50f),
        pill.closedBounds(height = 40f, detachProgress = 1f, layoutDirection = LayoutDirection.Rtl),
    )

    @Test
    fun `a button not detached leaves the pill whole in either direction`() = LayoutDirection.entries.forEach {
        assertEquals(
            Rect(left = 100f, top = 10f, right = 300f, bottom = 50f),
            pill.closedBounds(height = 40f, detachProgress = 0f, layoutDirection = it),
        )
    }
}
