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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performTextInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.resources.songs_tags_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test

/** The search field of the filter sheet's tag group, over the demo songs' tags (Demo, Easy, Singalong, and Fingerpicking). */
@OptIn(ExperimentalTestApi::class)
internal class SongFilterSearchTest {

    @Test
    fun `typing into the tag search leaves only the tags spelling it`() = runAppTest {
        tap(hasContentDescription(string(Res.string.songs_filter)))
        onNode(inSheet(hasSetTextAction())).performTextInput("SING")
        waitForIdle()
        onNode(inSheet(hasText("Singalong"))).assertExists()
        onNode(inSheet(hasText("Fingerpicking"))).assertDoesNotExist()
        onNode(inSheet(hasText("Demo"))).assertDoesNotExist()
    }

    @Test
    fun `a tag selected before the search stays in sight, and emptying the search brings every tag back`() = runAppTest {
        tap(hasContentDescription(string(Res.string.songs_filter)))
        tap(inSheet(hasText("Demo")))
        onNode(inSheet(hasSetTextAction())).performTextInput("easy")
        waitForIdle()
        onNode(inSheet(hasText("Easy"))).assertExists()
        onNode(inSheet(hasText("Demo"))).assertExists()
        onNode(inSheet(hasText("Singalong"))).assertDoesNotExist()
        tap(inSheet(hasContentDescription(string(Res.string.songs_clear))))
        onNode(inSheet(hasText("Singalong"))).assertExists()
    }

    @Test
    fun `a search no tag matches says so in place of the chips`() = runAppTest {
        tap(hasContentDescription(string(Res.string.songs_filter)))
        onNode(inSheet(hasText(string(Res.string.songs_tags_no_search_results)))).assertDoesNotExist()
        onNode(inSheet(hasSetTextAction())).performTextInput("polka")
        waitForIdle()
        onNode(inSheet(hasText(string(Res.string.songs_tags_no_search_results)))).assertExists()
    }
}

/** [matcher] inside the filter sheet, since the song cards under it show the same tags. */
private fun inSheet(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(isDialog())
