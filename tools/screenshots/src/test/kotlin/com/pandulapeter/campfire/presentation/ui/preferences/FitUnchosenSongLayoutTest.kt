/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_layout_pages
import com.pandulapeter.campfire.presentation.resources.song_layout_title
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class FitUnchosenSongLayoutTest {

    @Test
    fun `a layout picked and put away without Confirm lasts while its song is open and is fitted again once it is closed`() = runAppTest(
        window = TestWindow.PHONE,
        songLayout = SongLayout.CONTINUOUS,
        extraPreferences = mapOf("isSongLayoutChosen" to JsonPrimitive(false)),
    ) {
        openSong()
        waitUntil { onAllNodes(hasText(string(Res.string.song_layout_title))).fetchSemanticsNodes().isNotEmpty() }
        tap(hasText(string(Res.string.song_layout_pages)) and hasClickAction())
        waitUntil { songLayout() == SongLayout.PAGES }
        pressBack()
        waitForIdle()
        assertEquals(SongLayout.PAGES, songLayout(), "Putting the question away took the pick back.")

        resizeWindow(TestWindow.PHONE_ON_ITS_SIDE.width, TestWindow.PHONE_ON_ITS_SIDE.height)
        waitForIdle()
        resizeWindow(TestWindow.PHONE.width, TestWindow.PHONE.height)
        waitForIdle()
        assertEquals(SongLayout.PAGES, songLayout(), "Turning the phone under an open song took the pick back.")

        pressBack()
        waitUntil(timeoutMillis = FIT_TIMEOUT_MILLIS) { songLayout() == SongLayout.CONTINUOUS }
    }

    private fun AppTestScope.songLayout() = viewModel.preferencesController.userPreferences.value?.songLayout

    private companion object {
        const val FIT_TIMEOUT_MILLIS = 5_000L
    }
}
