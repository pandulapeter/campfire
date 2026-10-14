/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ToolsItemTest {

    @Test
    fun `a short window puts the tools behind one item on the rail`() = runAppTest(window = TestWindow(width = RAIL_WIDTH, height = 479.dp)) {
        onNodeWithText(string(Res.string.tools)).assertExists()
        tools().forEach { onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `a window just tall enough keeps every tool on the rail`() = runAppTest(window = TestWindow(width = RAIL_WIDTH, height = 480.dp)) {
        onNodeWithText(string(Res.string.tools)).assertDoesNotExist()
        tools().forEach { onNodeWithText(it).assertExists() }
    }

    private fun AppTestScope.tools() = listOf(string(Res.string.metronome), string(Res.string.tuner), string(Res.string.chords))
}

/** Wide enough for a rail rather than a navigation bar, which groups the tools at any height. */
private val RAIL_WIDTH = 800.dp
