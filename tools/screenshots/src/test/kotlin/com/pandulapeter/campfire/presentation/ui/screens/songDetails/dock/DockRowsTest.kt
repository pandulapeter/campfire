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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.opensTempoSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where an upright phone puts the tempo chip of song details' dock: at the end of the chord diagrams' row, in its bottom
 * end corner; and a song with nothing for a row lays the dock out as that row's switch turned off would.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockRowsTest {

    @Test
    fun `on an upright phone the tempo chip ends the diagrams' row and the key chip is the app bar's`() = runAppTest {
        openSong()
        waitForIdle()
        assertTempoChipEndsDiagramRow()
    }

    @Test
    fun `without scale suggestions the tempo chip still ends the diagrams' row`() = runAppTest(
        extraPreferences = mapOf("areScaleSuggestionsEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        assertTempoChipEndsDiagramRow()
    }

    @Test
    fun `a song without chords has the metronome panel for its dock, as with the diagrams and scale suggestions off`() = runAppTest(
        songs = listOf(SPOKEN_SONG_FILE),
    ) {
        tap(hasText(SPOKEN_SONG_TITLE) and hasClickAction())
        waitForIdle()
        onNode(opensTempoSheet()).assertDoesNotExist()
        onNode(hasContentDescription(string(Res.string.song_details_tempo_increase))).assertExists()
    }

    @Test
    fun `a song without chords and with the song's click off has no dock`() = runAppTest(
        songs = listOf(SPOKEN_SONG_FILE),
        extraPreferences = mapOf("isDockMetronomeEnabled" to JsonPrimitive(false)),
    ) {
        tap(hasText(SPOKEN_SONG_TITLE) and hasClickAction())
        waitForIdle()
        onNode(hasContentDescription(string(Res.string.song_details_tempo_increase))).assertDoesNotExist()
        onNode(hasContentDescription(string(Res.string.metronome_start)) and hasClickAction()).assertDoesNotExist()
    }

    /** The tempo chip stands after the diagram, within its row, under the app bar's key chip. */
    private fun AppTestScope.assertTempoChipEndsDiagramRow() {
        val tempoChip = tempoChip()
        val diagram = diagram()
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            tempoChip.left >= diagram.right && tempoChip.top < diagram.bottom && tempoChip.bottom > diagram.top,
            "The tempo chip at $tempoChip does not end the row of the diagram at $diagram.",
        )
        assertTrue(keyChip.bottom < diagram.top, "The key chip at $keyChip is in the dock, not the app bar.")
    }
}

/** A song of words alone, no chord in it. */
private const val SPOKEN_SONG_FILE = "campfire_tests-spoken_word_around_the_fire.cho"
private const val SPOKEN_SONG_TITLE = "Spoken Word Around the Fire"
