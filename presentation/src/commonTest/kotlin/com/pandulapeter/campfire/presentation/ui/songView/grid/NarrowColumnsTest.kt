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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NarrowColumnsTest {

    @Test
    fun `a song is set in narrow columns only where no more than a quarter of its lines wrap`() {
        val lineCount = IntArray(8) { 1 }
        assertTrue(fitsNarrowColumns(lineCount) { if (it < 2) 1 else 0 })
        assertFalse(fitsNarrowColumns(lineCount) { if (it < 3) 1 else 0 })
    }

    @Test
    fun `a line that wraps twice is one line that wraps`() {
        assertTrue(fitsNarrowColumns(IntArray(4) { 1 }) { if (it == 0) 2 else 0 })
    }

    @Test
    fun `what is no line of lyrics does not count`() {
        assertTrue(fitsNarrowColumns(intArrayOf(0, 0, 0, 1, 1, 1, 1)) { if (it < 3) 5 else 0 })
    }

    @Test
    fun `narrow columns are taken only where they save a page`() {
        val readable = ScoredGrid(emptyGrid(), pageCount = 2, score = 10.0)
        val narrow = ScoredGrid(emptyGrid(), pageCount = 1, score = 40.0)
        assertSame(narrow, withNarrowColumns(readable, listOf(narrow)))
    }

    @Test
    fun `narrow columns are never taken on a tie`() {
        val readable = ScoredGrid(emptyGrid(), pageCount = 2, score = 10.0)
        assertSame(readable, withNarrowColumns(readable, listOf(ScoredGrid(emptyGrid(), pageCount = 2, score = 1.0))))
    }

    @Test
    fun `of the narrow counts the best one is taken`() {
        val readable = ScoredGrid(emptyGrid(), pageCount = 3, score = 10.0)
        val better = ScoredGrid(emptyGrid(), pageCount = 2, score = 1.0)
        assertSame(better, withNarrowColumns(readable, listOf(ScoredGrid(emptyGrid(), pageCount = 2, score = 5.0), better)))
    }
}
