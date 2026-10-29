/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scale_major
import com.pandulapeter.campfire.presentation.resources.scales_diagram
import com.pandulapeter.campfire.presentation.resources.scales_sheet_title
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openScales
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ScalesFirstScreenTest {

    @Test
    fun `a phone shows scales on the Scales tab's first screen`() = runAppTest(window = TestWindow.PHONE) {
        openScales()
        // The "C Major: " prefix of the cell's description: the bare title also starts the major pentatonic and blues.
        val title = text(Res.string.scales_sheet_title, "C", string(Res.string.scale_major))
        val cell = onNode(hasContentDescription(text(Res.string.scales_diagram, title, ""), substring = true))
        cell.assertIsDisplayed()
        val cellTop = cell.fetchSemanticsNode().boundsInRoot.top
        val barTop = onNode(hasText(string(Res.string.songs)) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top
        assertTrue(cellTop <= barTop - MIN_VISIBLE_CELL_HEIGHT, "The first scale starts at $cellTop, its name hidden by the bar at $barTop.")
    }
}

/** Enough of a cell to read its name over the navigation bar, in px at the test's density of one. */
private const val MIN_VISIBLE_CELL_HEIGHT = 40f
