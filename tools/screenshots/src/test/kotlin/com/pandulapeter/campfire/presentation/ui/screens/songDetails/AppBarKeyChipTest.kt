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
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.screenshots.openPlayingSheet
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Song details' key chip and tuner in the app bar, where the dock would hold nothing else. */
@OptIn(ExperimentalTestApi::class)
internal class AppBarKeyChipTest {

    @Test
    fun `the key chip and the tuner are the app bar's where the dock would hold nothing else`() = runAppTest(
        extraPreferences = mapOf(
            "isDockMetronomeEnabled" to JsonPrimitive(false),
            "areChordDiagramsEnabled" to JsonPrimitive(false),
            "areScaleSuggestionsEnabled" to JsonPrimitive(false),
        ),
    ) {
        openSong()
        waitForIdle()
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot
        val tuner = onNode(hasContentDescription(string(Res.string.tuner)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(keyChip.bottom < APP_BAR_BOTTOM && tuner.bottom < APP_BAR_BOTTOM, "The key chip at $keyChip and the tuner at $tuner are not in the app bar.")
        assertTrue(tuner.left >= keyChip.right, "The tuner at $tuner does not follow the key chip at $keyChip.")
        openPlayingSheet()
        onNode(hasText(string(Res.string.song_details_dock_playing))).assertExists()
    }
}

/** Under the status bar and the app bar of the test's phone, and well above any song line under them, in dp. */
private const val APP_BAR_BOTTOM = 120f
