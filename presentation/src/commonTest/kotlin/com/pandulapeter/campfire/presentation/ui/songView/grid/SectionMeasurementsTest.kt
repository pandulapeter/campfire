/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionMeasurementsTest {

    private val measurements = SectionMeasurements(emptyList())
    private val key = SectionGridKey(
        settledWidth = 800,
        availableHeight = 600,
        maxRowHeight = 600,
        maxColumnCount = 2,
        endInset = 48,
        keepsEndInset = false,
    )
    private var searchCount = 0

    private fun search(): DecidedGrid {
        searchCount++
        return DecidedGrid(emptyGrid(), isInset = false)
    }

    @Test
    fun `a grid is searched for once for as long as its key stays the same`() {
        measurements.grid(key, search = ::search)
        measurements.grid(key, search = ::search)
        assertEquals(1, searchCount)
    }

    @Test
    fun `a grid is searched for again once its key changes`() {
        measurements.grid(key, search = ::search)
        measurements.grid(key.copy(settledWidth = 900), search = ::search)
        assertEquals(2, searchCount)
    }
}
