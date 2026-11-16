/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.spreadTheWord

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.spread_the_word_title
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class SpreadTheWordTest {

    @Test
    fun `the request to spread the word appears once on the way back from a song`() = runAppTest(
        songs = OWN_SONGS,
        // One open short of the ten, on the third day already.
        extraPreferences = mapOf(
            "songOpenCount" to JsonPrimitive(9),
            "songOpenDayCount" to JsonPrimitive(3),
            "lastSongOpenEpochDay" to JsonPrimitive(LocalDate.now().toEpochDay()),
        ),
    ) {
        val title = string(Res.string.spread_the_word_title)
        openOwnSong()
        pressBack()
        waitUntil(timeoutMillis = DIALOG_TIMEOUT_MILLIS) { onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        tap(hasText(string(Res.string.close)) and hasClickAction())
        waitUntil(timeoutMillis = DIALOG_TIMEOUT_MILLIS) { onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }

        openOwnSong()
        pressBack()
        waitForIdle()
        onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `the request waits for enough songs of the user's own`() = runAppTest(
        songs = OWN_SONGS.drop(1),
        extraPreferences = mapOf(
            "songOpenCount" to JsonPrimitive(9),
            "songOpenDayCount" to JsonPrimitive(3),
            "lastSongOpenEpochDay" to JsonPrimitive(LocalDate.now().toEpochDay()),
        ),
    ) {
        openOwnSong()
        pressBack()
        waitForIdle()
        onNodeWithText(string(Res.string.spread_the_word_title)).assertDoesNotExist()
    }

    /** The first song of the list, since the demo's sort under the test's own and out of a phone's view. */
    private fun AppTestScope.openOwnSong() = tap(hasText("A Long Tune") and hasClickAction())

    private companion object {
        val OWN_SONGS = listOf(
            "campfire_tests-a_tune.cho",
            "campfire_tests-a_second_tune.cho",
            "campfire_tests-a_long_tune.cho",
            "campfire_tests-the_long_road_north.cho",
            "campfire_tests-the_music_song.cho",
        )
        const val DIALOG_TIMEOUT_MILLIS = 5_000L
    }
}
