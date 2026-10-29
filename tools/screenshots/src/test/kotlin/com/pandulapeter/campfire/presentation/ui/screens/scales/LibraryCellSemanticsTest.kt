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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scale_major
import com.pandulapeter.campfire.presentation.resources.scales_diagram
import com.pandulapeter.campfire.presentation.resources.scales_sheet_title
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.screenshots.openChords
import com.pandulapeter.campfire.screenshots.openScales
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class LibraryCellSemanticsTest {

    @Test
    fun `a scale cell is a button with its description`() = runAppTest {
        openScales()
        val title = text(Res.string.scales_sheet_title, "C", string(Res.string.scale_major))
        onNode(hasClickAction() and hasContentDescription(text(Res.string.scales_diagram, title, ""), substring = true)).assertExists()
    }

    @Test
    fun `a chord cell is a button with its description`() = runAppTest {
        openChords()
        onNode(hasClickAction() and hasContentDescription(text(Res.string.song_details_chord_diagram, "C", ""), substring = true)).assertExists()
    }
}
