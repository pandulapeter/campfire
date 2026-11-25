/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_beat
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.song_details_dock_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.openTempoSheet
import com.pandulapeter.campfire.screenshots.opensTempoSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** The click in song details' dock: the tempo chip and the Tempo sheet it opens, and the dock that is only the metronome. */
@OptIn(ExperimentalTestApi::class)
internal class DockTempoChipTest {

    @Test
    fun `the tempo chip names the song's tempo and opens the Tempo sheet with its stepper and accents`() = runAppTest {
        openSong()
        onNode(hasContentDescription(string(Res.string.song_details_tempo, FILE_TEMPO)) and hasAnyAncestor(opensTempoSheet()), useUnmergedTree = true).assertExists()
        // The chip's beats only show the bar: they are tapped in the sheet.
        onNode(hasContentDescription(string(Res.string.metronome_beat, 1)) and hasClickAction()).assertDoesNotExist()
        openTempoSheet()
        onNode(hasText(string(Res.string.song_details_dock_tempo))).assertExists()
        onNode(hasContentDescription(string(Res.string.song_details_tempo_increase))).assertExists()
        onNode(hasContentDescription(string(Res.string.metronome_beat, 1)) and hasClickAction()).assertExists()
    }

    @Test
    fun `the tempo chip starts and stops the click in place`() = runAppTest {
        openSong()
        val start = hasContentDescription(string(Res.string.metronome_start)) and hasClickAction()
        val stop = hasContentDescription(string(Res.string.metronome_stop)) and hasClickAction()
        val before = onNode(start).fetchSemanticsNode().boundsInRoot
        tap(start)
        onNode(stop).assertExists()
        assertEquals(before, onNode(stop).fetchSemanticsNode().boundsInRoot)
        tap(stop)
        onNode(start).assertExists()
    }

    @Test
    fun `with the chords off the dock is the metronome panel - its stepper, accents, and play in place`() = runAppTest(
        extraPreferences = mapOf("isLyricsOnlyModeEnabled" to JsonPrimitive(true)),
    ) {
        openSong()
        onNode(opensTempoSheet()).assertDoesNotExist()
        onNode(hasContentDescription(string(Res.string.song_details_tempo_increase))).assertExists()
        onNode(hasContentDescription(string(Res.string.metronome_beat, 1)) and hasClickAction()).assertExists()
        onNode(hasContentDescription(string(Res.string.metronome_start)) and hasClickAction()).assertExists()
    }
}

/** The demo House of the Rising Sun's `{tempo}`. */
private const val FILE_TEMPO = "236"
