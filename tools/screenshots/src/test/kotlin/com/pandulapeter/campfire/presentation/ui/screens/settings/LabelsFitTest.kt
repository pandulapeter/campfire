/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_general
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_system_default
import com.pandulapeter.campfire.presentation.resources.songs_paste_from_clipboard
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class LabelsFitTest {

    @Test
    fun `the accidentals are named in full on the smallest phone in every language`() = APP_LANGUAGES.forEach { language ->
        listOf(1f, 1.3f).forEach { textScale ->
            runAppTest(appearance = TestAppearance(language = language, textScale = textScale)) {
                openSettingsTab(string(Res.string.settings_preferences))
                assertSegmentsWhole(
                    case = "${language.id} at $textScale",
                    labels = listOf(Res.string.settings_accidentals_original, Res.string.settings_accidentals_flats, Res.string.settings_accidentals_sharps).map { string(it) },
                )
            }
        }
    }

    @Test
    fun `the theme choices are named in full at large text in every language`() = APP_LANGUAGES.forEach { language ->
        runAppTest(appearance = TestAppearance(language = language, textScale = 1.3f)) {
            openSettingsTab(string(Res.string.settings_general))
            assertSegmentsWhole(
                case = language.id,
                labels = listOf(
                    Res.string.settings_user_interface_theme_light,
                    Res.string.settings_user_interface_theme_system_default,
                    Res.string.settings_user_interface_theme_dark,
                ).map { string(it) },
            )
        }
    }

    @Test
    fun `the paste action stays on one line on the smallest phone in every language`() = APP_LANGUAGES.forEach { language ->
        runAppTest(appearance = TestAppearance(language = language)) {
            val label = string(Res.string.songs_paste_from_clipboard)
            onNode(hasText(label) and hasClickAction()).performScrollTo()
            waitForIdle()
            val layout = textLayout(hasText(label) and hasAnyAncestor(hasClickAction()))
            assertEquals(1, layout.lineCount, "${language.id}: \"$label\" is broken onto ${layout.lineCount} lines.")
            assertTrue(layout.isWhole, "${language.id}: \"$label\" is cut short.")
        }
    }

    private fun AppTestScope.openSettingsTab(tab: String) {
        tap(hasText(string(Res.string.settings)) and hasClickAction())
        tap(hasText(tab) and hasClickAction())
    }

    /** Scrolls to the segments named [labels] and checks each label's own text, every place it is shown, whole. */
    private fun AppTestScope.assertSegmentsWhole(case: String, labels: List<String>) {
        onNode(hasText(labels.first()) and hasClickAction()).performScrollTo()
        waitForIdle()
        labels.forEach { label ->
            val nodes = onAllNodes(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty(), "$case: \"$label\" is not shown.")
            nodes.forEach { assertTrue(it.textLayout.isWhole, "$case: \"$label\" is cut short.") }
        }
    }
}
