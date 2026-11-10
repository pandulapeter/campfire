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

import androidx.compose.ui.layout.IntrinsicMeasurable
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionMeasurementsTest {

    private val measurements = SectionMeasurements(emptyList(), BooleanArray(0))
    private val key = SectionGridKey(
        settledWidth = 800,
        availableHeight = 600,
        maxRowHeight = 600,
        maxColumnCount = 2,
        endInset = 48,
        keepsEndInset = false,
        turnsByColumn = false,
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

    @Test
    fun `a section as tall at every width past its widest line is measured once for all of them, and at its widest`() {
        val section = Section(maxWidth = 100)
        val measurements = SectionMeasurements(listOf(SectionSizes()), booleanArrayOf(true))
        listOf(120, 150, 300).forEach { assertEquals(100, measurements.height(0, it, section)) }
        assertEquals(listOf(100), section.heightsAskedAt)
        measurements.height(0, 80, section)
        measurements.height(0, 60, section)
        assertEquals(listOf(100, 80, 60), section.heightsAskedAt)
    }

    @Test
    fun `any other section is measured at every width`() {
        val section = Section(maxWidth = 100)
        val measurements = SectionMeasurements(listOf(SectionSizes()), booleanArrayOf(false))
        listOf(120, 150, 300).forEach { measurements.height(0, it, section) }
        assertEquals(listOf(120, 150, 300), section.heightsAskedAt)
    }

    /** A section [maxWidth] wide at most, as tall as the width it is asked about, which it remembers. */
    private class Section(private val maxWidth: Int) : IntrinsicMeasurable {
        val heightsAskedAt = mutableListOf<Int>()
        override val parentData: Any? = null

        override fun minIntrinsicWidth(height: Int) = maxWidth

        override fun maxIntrinsicWidth(height: Int) = maxWidth

        override fun minIntrinsicHeight(width: Int) = width

        override fun maxIntrinsicHeight(width: Int) = width.also { heightsAskedAt += it }
    }
}
