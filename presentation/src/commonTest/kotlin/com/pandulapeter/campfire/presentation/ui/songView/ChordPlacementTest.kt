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

class ChordPlacementTest {

    private fun left(anchor: Float, previousChordEdge: Float, isRunRightToLeft: Boolean, isLineRightToLeft: Boolean) = chordLeft(
        anchor = anchor,
        chordWidth = 30f,
        rowWidth = 200f,
        previousChordEdge = previousChordEdge,
        isRunRightToLeft = isRunRightToLeft,
        isLineRightToLeft = isLineRightToLeft,
    )

    @Test
    fun `a chord starts at its character in a latin line`() {
        assertEquals(50f, left(anchor = 50f, previousChordEdge = 0f, isRunRightToLeft = false, isLineRightToLeft = false))
    }

    @Test
    fun `a chord ends at its character in a hebrew line`() {
        assertEquals(120f, left(anchor = 150f, previousChordEdge = 200f, isRunRightToLeft = true, isLineRightToLeft = true))
    }

    @Test
    fun `a latin word's chord in a hebrew line starts over its first letter`() {
        assertEquals(80f, left(anchor = 80f, previousChordEdge = 160f, isRunRightToLeft = false, isLineRightToLeft = true))
    }

    @Test
    fun `a latin word's chord in a hebrew line is pushed leftwards off the chord before it`() {
        assertEquals(100f, left(anchor = 120f, previousChordEdge = 130f, isRunRightToLeft = false, isLineRightToLeft = true))
    }

    @Test
    fun `a hebrew word's chord in a latin line ends over its first letter and is pushed rightwards off the chord before it`() {
        assertEquals(70f, left(anchor = 100f, previousChordEdge = 20f, isRunRightToLeft = true, isLineRightToLeft = false))
        assertEquals(90f, left(anchor = 100f, previousChordEdge = 90f, isRunRightToLeft = true, isLineRightToLeft = false))
    }

    @Test
    fun `a chord is kept inside the row where the chord before it allows`() {
        assertEquals(170f, left(anchor = 190f, previousChordEdge = 0f, isRunRightToLeft = false, isLineRightToLeft = false))
        assertEquals(0f, left(anchor = 10f, previousChordEdge = 200f, isRunRightToLeft = true, isLineRightToLeft = true))
        assertEquals(0f, left(anchor = -10f, previousChordEdge = 200f, isRunRightToLeft = false, isLineRightToLeft = true))
    }
}
