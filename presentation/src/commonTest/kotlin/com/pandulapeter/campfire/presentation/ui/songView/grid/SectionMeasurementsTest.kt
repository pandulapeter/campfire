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
    private val searches = mutableListOf<Boolean>()

    /** A song that could be fitted: its grid is only deferred where fitting was not allowed. */
    private fun fittableSearch(canFit: Boolean): DecidedGrid {
        searches += canFit
        return DecidedGrid(emptyGrid(), isInset = false, fitScale = 1f, isFitDeferred = !canFit)
    }

    @Test
    fun `a grid decided with fitting deferred is searched again once fitting is allowed`() {
        measurements.grid(key, canFit = false, search = ::fittableSearch)
        measurements.grid(key, canFit = true, search = ::fittableSearch)
        assertEquals(listOf(false, true), searches)
    }

    @Test
    fun `a grid decided with fitting is not searched again`() {
        measurements.grid(key, canFit = true, search = ::fittableSearch)
        measurements.grid(key, canFit = false, search = ::fittableSearch)
        measurements.grid(key, canFit = true, search = ::fittableSearch)
        assertEquals(listOf(true), searches)
    }

    @Test
    fun `a deferred grid is not searched again while fitting stays deferred`() {
        measurements.grid(key, canFit = false, search = ::fittableSearch)
        measurements.grid(key, canFit = false, search = ::fittableSearch)
        assertEquals(listOf(false), searches)
    }

    @Test
    fun `a grid that could not have been fitted is not searched again`() {
        val search = { canFit: Boolean ->
            searches += canFit
            DecidedGrid(emptyGrid(), isInset = false, fitScale = 1f, isFitDeferred = false)
        }
        measurements.grid(key, canFit = false, search = search)
        measurements.grid(key, canFit = true, search = search)
        assertEquals(listOf(false), searches)
    }
}
