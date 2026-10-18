/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ListItemMetricsTest {

    @Test
    fun `without the scroller's column the controls move furthest toward the edge`() = assertEquals(16.dp, listItemTrailingKeylineAdjustment(0.dp))

    @Test
    fun `with the whole column they move the least`() = assertEquals(12.dp, listItemTrailingKeylineAdjustment(24.dp))

    @Test
    fun `a column halfway open moves them halfway`() = assertEquals(14.dp, listItemTrailingKeylineAdjustment(12.dp))

    @Test
    fun `a spring overshooting the column moves them no less than the whole column does`() =
        assertEquals(12.dp, listItemTrailingKeylineAdjustment(30.dp))
}
