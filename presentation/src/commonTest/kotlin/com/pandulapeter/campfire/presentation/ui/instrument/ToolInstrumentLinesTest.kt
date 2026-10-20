/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolInstrumentLinesTest {

    private fun shares(
        lineWidth: Int = 336,
        trailingWidth: Int = 214,
        pillWidth: Int,
        hasTuning: Boolean = true,
        nameRoom: Int = 29,
        minNameWidth: Int = 14,
    ) = pillSharesLine(
        lineWidth = lineWidth,
        trailingWidth = trailingWidth,
        gap = 8,
        pillWidth = pillWidth,
        hasTuning = hasTuning,
        nameRoom = nameRoom,
        minNameWidth = minNameWidth,
    )

    private fun places(progress: Float, lineWidth: Int = 336, trailingWidth: Int = 263) = toolLinePlaces(
        progress = progress,
        lineWidth = lineWidth,
        lineHeight = 56,
        gap = 8,
        pillHeight = 52,
        trailingWidth = trailingWidth,
        trailingHeight = 52,
    )

    @Test
    fun `a pill that fits whole shares the line`() {
        assertTrue(shares(pillWidth = 100))
    }

    @Test
    fun `a cut pill whose share still shows the tuning shares the line`() {
        assertTrue(shares(pillWidth = 161))
    }

    @Test
    fun `at the largest text size the pill takes a line of its own`() {
        assertFalse(shares(trailingWidth = 263, pillWidth = 263, minNameWidth = 28))
    }

    @Test
    fun `exactly the least of a name shares the line and a pixel less does not`() {
        assertTrue(shares(pillWidth = 161, minNameWidth = 17))
        assertFalse(shares(pillWidth = 161, minNameWidth = 18))
    }

    @Test
    fun `without a tuning half the instrument's name is the one asked about`() {
        assertFalse(shares(trailingWidth = 336 - 8 - 60, pillWidth = 300, hasTuning = false, nameRoom = 32, minNameWidth = 42))
        assertTrue(shares(trailingWidth = 336 - 8 - 100, pillWidth = 300, hasTuning = false, nameRoom = 32, minNameWidth = 42))
    }

    @Test
    fun `an unbounded line is shared`() {
        assertTrue(shares(lineWidth = Constraints.Infinity, pillWidth = 300, minNameWidth = 28))
    }

    @Test
    fun `beside the pill the controls are centred in the line, which keeps its height`() {
        assertEquals(ToolLinePlaces(pillMaxWidth = 336 - 263 - 8, trailingY = 2, height = 56), places(0f))
    }

    @Test
    fun `under the pill the controls stand a gap below it and the pill may take the whole line`() {
        val under = places(1f)
        assertEquals(54 + 8, under.trailingY)
        assertEquals(336, under.pillMaxWidth)
    }

    @Test
    fun `a spring overshooting either end is held at that end`() {
        assertEquals(places(0f), places(-0.2f))
        assertEquals(places(1f), places(1.2f))
    }

    @Test
    fun `controls wider than the line leave the pill no room rather than a negative one`() {
        assertEquals(0, places(0f, lineWidth = 200).pillMaxWidth)
    }

    @Test
    fun `throughout the slide the controls keep their distance to the line's end and the pill never reaches under them`() {
        val start = places(0f)
        val slack = start.height - start.trailingY - 52
        var previous = start
        for (step in 0..20) {
            val current = places(step * 0.05f)
            assertTrue(abs(current.height - current.trailingY - 52 - slack) <= 1, "Slack at $step: $current")
            if (current.pillMaxWidth > 336 - 263 - 8) assertTrue(current.trailingY >= 54, "The pill reaches under at $step: $current")
            assertTrue(current.trailingY >= previous.trailingY && current.pillMaxWidth >= previous.pillMaxWidth, "Back at $step: $current")
            previous = current
        }
    }
}
