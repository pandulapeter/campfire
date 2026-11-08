/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_capo_keeps_key
import com.pandulapeter.campfire.presentation.resources.settings_general
import com.pandulapeter.campfire.presentation.resources.settings_group_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_group_performance
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openChordsPreferences
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class PreferencesSheetScrollTest {

    @Test
    fun `each page of the preferences sheet keeps its own scroll position`() = runAppTest(window = TestWindow.PHONE) {
        openSong()
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.settings_preferences)) and hasClickAction())
        openChordsPreferences()
        onNode(hasText(string(Res.string.settings_capo_keeps_key))).performScrollTo()
        waitForIdle()

        // Like the Chords button, General's names its page only to accessibility services while another is picked.
        tap(hasContentDescription(string(Res.string.settings_general)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNode(hasText(string(Res.string.settings_group_performance))).assertIsDisplayed()

        openChordsPreferences()
        onNode(hasText(string(Res.string.settings_group_diagrams))).assertIsNotDisplayed()
        onNode(hasText(string(Res.string.settings_capo_keeps_key))).assertIsDisplayed()
    }
}
