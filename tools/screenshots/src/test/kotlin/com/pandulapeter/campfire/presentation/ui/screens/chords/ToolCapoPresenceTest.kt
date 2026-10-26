/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_instrument_other
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_other
import com.pandulapeter.campfire.presentation.resources.scales_instrument_other
import com.pandulapeter.campfire.presentation.resources.song_details_capo_increase
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openChords
import com.pandulapeter.campfire.screenshots.openScales
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ToolCapoPresenceTest {

    @Test
    fun `the tools' capo is there for the guitar, and an instrument with nothing to capo hides it on both tabs`() = runAppTest(window = TALL_PHONE) {
        openChords()
        val capoUp = hasContentDescription(string(Res.string.song_details_capo_increase))
        onAllNodes(capoUp).assertCountEquals(1)
        tap(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        tap(hasText(string(Res.string.instrument_other)) and hasClickAction())
        onNode(hasText(string(Res.string.chords_instrument_other))).assertIsDisplayed()
        waitUntil { onAllNodes(capoUp).fetchSemanticsNodes().isEmpty() }
        openScales()
        onNode(hasText(string(Res.string.scales_instrument_other))).assertIsDisplayed()
        onAllNodes(capoUp).assertCountEquals(0)
    }
}

/** Tall enough for the instrument sheet to list every instrument without scrolling. */
private val TALL_PHONE = TestWindow(width = 360.dp, height = 1100.dp)
