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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor's field lays its lines out in its song's direction, whatever the interface's. */
@OptIn(ExperimentalTestApi::class)
internal class EditorDirectionTest {

    @Test
    fun `a Hebrew song's lines are laid out from the right in an English interface`() = runAppTest(songs = listOf(SONG_FILE)) {
        tap(hasText(SONG_TITLE) and hasClickAction())
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.songs_edit_song)) and hasClickAction())
        val (text, layout) = fieldLayout()
        listOf("{title: Shir Laderech}", "Hey").forEach { line ->
            val index = layout.getLineForOffset(text.indexOf(line))
            assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(text.indexOf(line)), line)
            assertTrue(layout.getLineLeft(index) > 0f && layout.getLineRight(index) >= layout.size.width - 1f, "$line ends at ${layout.getLineRight(index)} of ${layout.size.width}.")
        }
    }

    @Test
    fun `an English song's lines are laid out from the left in a right-to-left interface`() = runAppTest(
        appearance = TestAppearance(layoutDirection = LayoutDirection.Rtl),
    ) {
        openEditor()
        val (_, layout) = fieldLayout()
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        assertEquals(0f, layout.getLineLeft(0))
    }

    /** The text of the editor's field and its layout. */
    private fun AppTestScope.fieldLayout(): Pair<String, TextLayoutResult> {
        val config = onNode(hasSetTextAction()).fetchSemanticsNode().config
        val layouts = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return config[SemanticsProperties.EditableText].text to layouts.single()
    }
}

private const val SONG_FILE = "campfire_tests-shir_laderech.cho"
private const val SONG_TITLE = "Shir Laderech"
