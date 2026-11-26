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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.instrument_violin
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The app's instrument and tuning pill at the start of song details' dock: before the diagrams in their row, its tuning
 * under the instrument, at the side panel's head, and at the start of a short window's one row; opening the instrument's
 * sheet, read only mode's too; on an instrument without shapes too, which has no diagrams; and nowhere the diagrams are
 * switched off.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockInstrumentPillTest {

    @Test
    fun `on an upright phone the instrument pill starts the diagrams' row and opens the instrument's sheet`() = runAppTest {
        openSong()
        waitForIdle()
        val pill = instrumentPill()
        val diagram = diagram()
        assertTrue(
            pill.right <= diagram.left && pill.top < diagram.bottom && pill.bottom > diagram.top,
            "The instrument pill at $pill does not start the row of the diagram at $diagram.",
        )
        tap(instrumentPillNode())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
    }

    @Test
    fun `the tall instrument pill's tuning under the instrument opens the tuning's sheet`() = runAppTest {
        openSong()
        waitForIdle()
        val instrument = instrumentPill()
        val tuning = onNode(tuningHalfNode()).fetchSemanticsNode().boundsInRoot
        assertTrue(tuning.top >= instrument.bottom - 1f, "The tuning half at $tuning is not under the instrument's at $instrument.")
        tap(tuningHalfNode())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuning }
    }

    @Test
    fun `a wide window has the instrument pill at the side panel's head, within its width`() = runAppTest(window = TestWindow.DESKTOP) {
        openSong()
        waitForIdle()
        val pill = instrumentPill()
        val diagram = diagram()
        val tempoChip = tempoChip()
        assertTrue(pill.bottom <= diagram.top, "The instrument pill at $pill is not over the panel's diagram at $diagram.")
        assertTrue(
            pill.left >= tempoChip.left - PANEL_SLACK && pill.right <= tempoChip.right + PANEL_SLACK && pill.width <= MAX_PANEL_WIDTH,
            "The instrument pill at $pill is not within the panel of the tempo chip at $tempoChip.",
        )
    }

    @Test
    fun `a phone on its side starts its one row with the instrument pill, as high as the tempo chip`() = runAppTest(window = TestWindow.PHONE_ON_ITS_SIDE) {
        openSong()
        waitForIdle()
        val pill = instrumentPill()
        val tempoChip = tempoChip()
        assertTrue(pill.right <= tempoChip.left && pill.height <= tempoChip.height + 1f, "The instrument pill at $pill is not a chip of the tempo chip's row at $tempoChip.")
    }

    @Test
    fun `read only mode keeps the instrument pill, which still opens the instrument's sheet`() = runAppTest(
        extraPreferences = mapOf("isPerformanceModeEnabled" to JsonPrimitive(true)),
    ) {
        openSong()
        waitForIdle()
        tap(instrumentPillNode())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
    }

    @Test
    fun `with the diagrams off the instrument pill stays while it holds the tuner`() = runAppTest(
        extraPreferences = mapOf("areChordDiagramsEnabled" to JsonPrimitive(false), "isDockTunerEnabled" to JsonPrimitive(true)),
    ) {
        openSong()
        waitForIdle()
        onNode(instrumentPillNode()).assertExists()
    }

    @Test
    fun `with the diagrams and the dock's tuner off there is no instrument pill`() = runAppTest(
        extraPreferences = mapOf("areChordDiagramsEnabled" to JsonPrimitive(false), "isDockTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        onNode(instrumentPillNode()).assertDoesNotExist()
    }

    @Test
    fun `an instrument without shapes keeps its pill in the dock, which changes it back`() = runAppTest(
        extraPreferences = mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.VIOLIN.id)),
    ) {
        openSong()
        waitForIdle()
        tap(hasText(string(Res.string.instrument_violin)) and hasClickAction())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
    }

    @Test
    fun `a song with no diagram to draw has no instrument pill`() = runAppTest(songs = listOf(SPOKEN_SONG_FILE)) {
        tap(hasText(SPOKEN_SONG_TITLE) and hasClickAction())
        waitForIdle()
        onNode(instrumentPillNode()).assertDoesNotExist()
    }

    /** The pill's instrument half, which the pill's place is measured by. */
    private fun AppTestScope.instrumentPillNode() = hasText(string(Res.string.instrument_guitar)) and hasClickAction()

    private fun AppTestScope.tuningHalfNode() = hasText(string(Res.string.instrument_tuning_standard)) and hasClickAction()

    private fun AppTestScope.instrumentPill(): Rect = onNode(instrumentPillNode()).fetchSemanticsNode().boundsInRoot
}

/** The side panel's widest, its padding included (`dockPanelWidthOf`), in dp. */
private const val MAX_PANEL_WIDTH = 176f

/** How far past the tempo chip's sides the pill may reach: both are as wide as the panel's content, give or take a pixel. */
private const val PANEL_SLACK = 1f

/** A song of words alone, no chord in it. */
private const val SPOKEN_SONG_FILE = "campfire_tests-spoken_word_around_the_fire.cho"
private const val SPOKEN_SONG_TITLE = "Spoken Word Around the Fire"
