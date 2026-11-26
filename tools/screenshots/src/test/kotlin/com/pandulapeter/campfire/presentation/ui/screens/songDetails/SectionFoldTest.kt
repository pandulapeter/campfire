/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_expand
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A song's fold headers on song details, where a tap is the song's to step: an unfolded one folds only on a long press,
 * and a folded one still unfolds on a tap. The header of the song's first section, on its first page, stands in for them all.
 */
@OptIn(ExperimentalTestApi::class)
internal class SectionFoldTest {

    @Test
    fun `a tap on an unfolded header leaves it unfolded, its long press folds it, and a tap unfolds it again`() = runAppTest(songs = listOf(LONG_SONG_FILE)) {
        openSong(LONG_SONG_TITLE)
        val header = hasText(INTRO) and hasClickAction()
        tap(header)
        assertTrue(!isIntroFolded(), "A tap on an unfolded header folded it.")
        onNode(header).performTouchInput { longClick() }
        waitForIdle()
        waitUntil { isIntroFolded() }
        tap(header)
        waitUntil { !isIntroFolded() }
    }

    /** Whether a section's header offers to show it, which only a folded one does. */
    private fun AppTestScope.isIntroFolded() = onAllNodes(
        hasContentDescription(string(Res.string.song_details_section_expand), substring = true),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()
}

private const val LONG_SONG_FILE = "campfire_tests-the_long_road_north.cho"
private const val LONG_SONG_TITLE = "The Long Road North"
/** The song's first section, a tab under a name of its own, which heads it as a section's header does. */
private const val INTRO = "Intro riff"
