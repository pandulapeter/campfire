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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.openChords
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ToolCapoLineTest {

    @Test
    fun `on the smallest phone in German the capo stands under a whole instrument pill`() = runAppTest(
        appearance = TestAppearance(language = AppLanguage.GERMAN),
    ) {
        openChords()
        assertWhole(string(Res.string.instrument_guitar))
        assertWhole(string(Res.string.instrument_tuning_standard))
        val instrument = onNode(hasText(string(Res.string.instrument_guitar)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val capo = onNodeWithText(string(Res.string.song_editor_insert_capo), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(capo.top >= instrument.bottom, "The capo at $capo is beside the instrument at $instrument.")
    }

    /**
     * Asserts that the name [text] is drawn whole: on its one line, and at least as wide as its natural width. Neither
     * `isLineEllipsized`, which Skiko's paragraph always answers false, nor `didOverflowWidth`, which a name narrower than
     * its room also reports (the paragraph is laid out at the room's width), can tell.
     */
    private fun AppTestScope.assertWhole(text: String) {
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
        val naturalWidth = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
        assertTrue(!layout.multiParagraph.didExceedMaxLines && naturalWidth <= layout.size.width, "\"$text\" is cut short.")
    }
}
