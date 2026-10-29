/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorRevealTest {

    @Test
    fun `a line inside the uncovered strip is not moved`() {
        assertNull(revealTarget(lineTop = 1_050f, lineBottom = 1_100f, scroll = 1_000, uncoveredHeight = 200f, margin = 48f))
    }

    @Test
    fun `a line in the top part of the pane but below the strip is revealed`() {
        // Within the top 40% of a 1000px pane, but behind a sheet that leaves only 200px of it.
        assertEquals(1_302, revealTarget(lineTop = 1_350f, lineBottom = 1_390f, scroll = 1_000, uncoveredHeight = 200f, margin = 48f))
    }

    @Test
    fun `a line above the pane is revealed the margin below its top`() {
        assertEquals(452, revealTarget(lineTop = 500f, lineBottom = 540f, scroll = 1_000, uncoveredHeight = 400f, margin = 48f))
    }

    @Test
    fun `the margin is at most a third of a narrow strip`() {
        assertEquals(470, revealTarget(lineTop = 500f, lineBottom = 540f, scroll = 1_000, uncoveredHeight = 90f, margin = 144f))
    }

    @Test
    fun `a target never goes above the start of the text`() {
        assertEquals(0, revealTarget(lineTop = 10f, lineBottom = 50f, scroll = 300, uncoveredHeight = 400f, margin = 48f))
    }

    @Test
    fun `nothing is revealed where the sheet leaves no room`() {
        assertNull(revealTarget(lineTop = 500f, lineBottom = 540f, scroll = 1_000, uncoveredHeight = 0f, margin = 48f))
        assertNull(revealTarget(lineTop = 500f, lineBottom = 540f, scroll = 1_000, uncoveredHeight = -20f, margin = 48f))
    }

    @Test
    fun `a caret already in view is not scrolled to sideways`() {
        assertNull(horizontalRevealTarget(x = 300f, scroll = 0, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = false))
        // 1,700 from the left is 300 from the right, where a right-to-left field starts.
        assertNull(horizontalRevealTarget(x = 1_700f, scroll = 0, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = true))
    }

    @Test
    fun `a caret past the end of the pane is scrolled to the margin after the start`() {
        assertEquals(952, horizontalRevealTarget(x = 1_000f, scroll = 0, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = false))
        assertEquals(952, horizontalRevealTarget(x = 1_000f, scroll = 0, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = true))
    }

    @Test
    fun `a right-to-left caret back near the right edge scrolls the field back to its start`() {
        assertEquals(0, horizontalRevealTarget(x = 1_980f, scroll = 600, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = true))
    }

    @Test
    fun `a left-to-right caret within the margin of the pane's left edge is revealed`() {
        assertEquals(552, horizontalRevealTarget(x = 600f, scroll = 580, viewportWidth = 500, contentWidth = 2_000, margin = 48f, isRightToLeft = false))
    }
}
