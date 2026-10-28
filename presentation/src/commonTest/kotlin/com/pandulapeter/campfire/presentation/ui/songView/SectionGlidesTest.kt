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

import androidx.compose.ui.unit.IntOffset
import com.pandulapeter.campfire.presentation.ui.songView.grid.DecidedGrid
import com.pandulapeter.campfire.presentation.ui.songView.grid.emptyGrid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The glides are launched on the test's background scope and never run, so an offset is the jump a pass added. */
class SectionGlidesTest {

    private val grid = DecidedGrid(emptyGrid(), isInset = false)

    @Test
    fun `a tune's first rendering places the sections at once`() = runTest {
        val glides = SectionGlides(backgroundScope, SongMotionSpec)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 0)), glidesEveryMove = true, arrival = null)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 100)), glidesEveryMove = true, arrival = ARRIVAL)
        assertEquals(IntOffset.Zero, glides.offsetOf(KEY))
    }

    @Test
    fun `the move after an arrival glides again`() = runTest {
        val glides = SectionGlides(backgroundScope, SongMotionSpec)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 0)), glidesEveryMove = true, arrival = null)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 100)), glidesEveryMove = true, arrival = ARRIVAL)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 200)), glidesEveryMove = true, arrival = ARRIVAL)
        assertEquals(IntOffset(0, -100), glides.offsetOf(KEY))
    }

    @Test
    fun `a move with no arrival glides`() = runTest {
        val glides = SectionGlides(backgroundScope, SongMotionSpec)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 0)), glidesEveryMove = true, arrival = null)
        glides.follow(KEYS, grid, arrayOf(IntOffset(0, 100)), glidesEveryMove = true, arrival = null)
        assertEquals(IntOffset(0, -100), glides.offsetOf(KEY))
    }

    private companion object {
        const val KEY = "verse"
        val KEYS = listOf<Any>(KEY)
        val ARRIVAL = Any()
    }
}
