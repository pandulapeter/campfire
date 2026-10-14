/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_select_multiple
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class SongListAccessibilityTest {

    @Test
    fun `a song card is one node with its title and artist`() = runAppTest {
        onNode(hasText(RISING_SUN) and hasText(ARTIST) and hasClickAction()).assertExists()
    }

    @Test
    fun `the selection mode tells whether each card is ticked`() = runAppTest {
        onNode(hasText(RISING_SUN) and hasClickAction()).performTouchInput { longClick() }
        onNodeWithText(string(Res.string.songs_select_multiple)).performClick()
        // The ticked song is also listed again at the top, in the group of the selected ones.
        onAllNodes(hasText(RISING_SUN) and hasClickAction()).assertCountEquals(2).assertAll(isOn())
        onAllNodes(hasText(HOME_ON_THE_RANGE, substring = true) and hasClickAction()).assertCountEquals(1).assertAll(isOff())
    }
}

private const val RISING_SUN = "House of the Rising Sun"
private const val HOME_ON_THE_RANGE = "Home on the Range"
private const val ARTIST = "Traditional American"
