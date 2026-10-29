/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.tap
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A Hebrew song in the English interface: its body reads from the right, so even its one Latin line, which would start
 * a left-to-right paragraph of its own, ends at the right edge with the Hebrew lines around it.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongDirectionTest {

    @Test
    fun `a Hebrew song's lines are laid out from the right in an English interface`() = runAppTest(songs = listOf(SONG_FILE)) {
        tap(hasText(SONG_TITLE) and hasClickAction())
        val latin = textLayoutOf(LATIN_LINE)
        assertTrue(latin.getLineLeft(0) > latin.size.width / 2f, "The Latin line starts at ${latin.getLineLeft(0)} of ${latin.size.width}.")
        assertTrue(latin.getParagraphDirection(0) == ResolvedTextDirection.Rtl, "The Latin line is not a right-to-left paragraph.")
    }

    /** The layout of the one text on screen that is exactly [text]. */
    private fun AppTestScope.textLayoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }
}

private const val SONG_FILE = "campfire_tests-shir_laderech.cho"
private const val SONG_TITLE = "Shir Laderech"
private const val LATIN_LINE = "Hey"
