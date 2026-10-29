/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scale_dominant_diminished
import com.pandulapeter.campfire.presentation.resources.scales_diagram
import com.pandulapeter.campfire.presentation.resources.scales_labels_show_notes
import com.pandulapeter.campfire.presentation.resources.scales_sheet_title
import com.pandulapeter.campfire.presentation.ui.components.PINNED_SEARCH_INDEX
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.openScales
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ScaleSheetTitleTest {

    @Test
    fun `a phone's scale sheet names a long scale in full`() = runAppTest(window = TestWindow.PHONE) {
        assertLongTitleInFull()
    }

    @Test
    fun `a large text size still names the scale in full`() = runAppTest(window = TestWindow.PHONE, appearance = TestAppearance(textScale = 2f)) {
        assertLongTitleInFull()
    }

    private fun AppTestScope.assertLongTitleInFull() {
        openScales()
        // The controls leave the search field below the grid's end on a phone, where it is not composed and its result would not be.
        onNode(hasScrollToIndexAction()).performScrollToIndex(PINNED_SEARCH_INDEX)
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput(string(Res.string.scale_dominant_diminished))
        waitForIdle()
        val title = text(Res.string.scales_sheet_title, "C", string(Res.string.scale_dominant_diminished))
        // The cell is named "title: notes"; the prefix keeps a longer name starting with the same words from matching.
        tap(hasContentDescription(text(Res.string.scales_diagram, title, ""), substring = true))
        val heading = onNode(hasText(title) and isHeading()).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { heading.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
        assertTrue(layout.isWhole, "The sheet cuts \"$title\" short.")
        onNode(hasContentDescription(string(Res.string.scales_labels_show_notes))).assertExists()
    }
}
