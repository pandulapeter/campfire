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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_other
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openPlayingSheet
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where song details offers the tuner, as the Dock section's Tuner switch and the Tuner feature say: in the dock's
 * instrument card with the switch on, and the song's menu then without it; in the menu with the switch off, or wherever
 * the card is not shown; nowhere with the feature off. The Playing sheet keeps its own throughout.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockTunerTest {

    @Test
    fun `with the dock's tuner on, the instrument card ends in the tuner and the menu has none`() = runAppTest {
        openSong()
        waitForIdle()
        val pill = onNode(hasText(string(Res.string.instrument_guitar)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val tuner = dockTuner()
        assertTrue(tuner.left >= pill.right && tuner.right <= diagram().left, "The tuner at $tuner is not between the instrument at $pill and the diagrams.")
        assertTrue(tuner.top >= pill.top - 1f && tuner.bottom <= pill.bottom + pill.height, "The tuner at $tuner is not on the card of the instrument at $pill.")
        assertMenuTuner(isThere = false)
        pressBack()
        tap(dockTunerNode())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuner }
    }

    @Test
    fun `the instrument card is as tall as the tempo chip beside the diagrams, and about as wide on a phone`() = runAppTest {
        openSong()
        waitForIdle()
        val tuner = dockTuner()
        val tempoChip = tempoChip()
        val pill = onNode(hasText(string(Res.string.instrument_guitar)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(tuner.bottom <= tempoChip.bottom && tuner.top >= tempoChip.top, "The tuner at $tuner is not within the tempo chip's height at $tempoChip.")
        assertTrue(tempoChip.width <= MAX_PHONE_CARD_WIDTH, "The tempo chip is ${tempoChip.width} dp wide on a phone.")
        assertTrue(tuner.right - pill.left <= MAX_PHONE_CARD_WIDTH, "The instrument card is ${tuner.right - pill.left} dp wide on a phone.")
    }

    @Test
    fun `with the dock's tuner off, the menu holds the tuner and the card has none`() = runAppTest(
        extraPreferences = mapOf("isDockTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        onNode(dockTunerNode()).assertDoesNotExist()
        onNode(hasText(string(Res.string.instrument_guitar)) and hasClickAction()).assertExists()
        assertMenuTuner(isThere = true)
        tap(hasText(string(Res.string.tuner)) and hasAnyAncestor(isPopup()))
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuner }
    }

    @Test
    fun `with the tuner feature off there is no tuner anywhere, the Playing sheet and the U key included`() = runAppTest(
        extraPreferences = mapOf("isTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        onNode(dockTunerNode()).assertDoesNotExist()
        assertMenuTuner(isThere = false)
        pressBack()
        onRoot().performKeyInput { pressKey(Key.U) }
        waitForIdle()
        assertEquals(null, viewModel.dialogHost.visibleDialog.value)
        openPlayingSheet()
        onNode(hasContentDescription(string(Res.string.tuner)) and hasClickAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
    }

    @Test
    fun `with the diagrams off there is no card, so the menu holds the tuner whatever the switch says`() = runAppTest(
        extraPreferences = mapOf("areChordDiagramsEnabled" to JsonPrimitive(false), "isDockTunerEnabled" to JsonPrimitive(true)),
    ) {
        openSong()
        waitForIdle()
        onNode(dockTunerNode()).assertDoesNotExist()
        assertMenuTuner(isThere = true)
    }

    @Test
    fun `a wide window's side panel has the tuner in its instrument card`() = runAppTest(window = TestWindow.DESKTOP) {
        openSong()
        waitForIdle()
        val tuner = dockTuner()
        val diagram = diagram()
        assertTrue(tuner.bottom <= diagram.top, "The tuner at $tuner is not over the panel's diagram at $diagram.")
        assertMenuTuner(isThere = false)
    }

    @Test
    fun `an instrument without shapes keeps a one-line card with the tuner, the way back to an instrument with diagrams`() = runAppTest(
        extraPreferences = mapOf("instrument" to JsonPrimitive(UserPreferences.Instrument.OTHER.id)),
    ) {
        openSong()
        waitForIdle()
        val instrument = onNode(hasText(string(Res.string.instrument_other)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val tuner = dockTuner()
        val tempoChip = tempoChip()
        assertTrue(tuner.left >= instrument.right && tuner.right <= tempoChip.left, "The tuner at $tuner does not follow the instrument at $instrument.")
        assertTrue(instrument.height <= tempoChip.height + 1f, "The card at $instrument is not a chip of the tempo chip's row at $tempoChip.")
        assertMenuTuner(isThere = false)
        pressBack()
        tap(hasText(string(Res.string.instrument_other)) and hasClickAction())
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
    }

    @Test
    fun `an instrument without shapes keeps its card in a dock that has nothing else`() = runAppTest(
        extraPreferences = mapOf(
            "instrument" to JsonPrimitive(UserPreferences.Instrument.OTHER.id),
            "isMetronomeEnabled" to JsonPrimitive(false),
            "areScaleSuggestionsEnabled" to JsonPrimitive(false),
        ),
    ) {
        openSong()
        waitForIdle()
        onNode(hasText(string(Res.string.instrument_other)) and hasClickAction()).assertExists()
        onNode(dockTunerNode()).assertExists()
    }

    /** The dock's tuner: the one said as the tuner outside the menu and any sheet. */
    private fun AppTestScope.dockTunerNode() = hasContentDescription(string(Res.string.tuner)) and hasClickAction()

    private fun AppTestScope.dockTuner(): Rect = onNode(dockTunerNode()).fetchSemanticsNode().boundsInRoot

    /** Opens the song's menu and checks whether it holds the tuner, leaving the menu open. */
    private fun AppTestScope.assertMenuTuner(isThere: Boolean) {
        tap(hasContentDescription(string(Res.string.songs_actions)))
        val entries = onAllNodes(hasText(string(Res.string.tuner)) and hasAnyAncestor(isPopup())).fetchSemanticsNodes()
        assertEquals(isThere, entries.isNotEmpty(), "The song's menu ${if (isThere) "has no" else "has a"} tuner.")
    }
}

/** About the 100 dp the owner asked of each card beside the diagrams on an upright phone, with a little to spare. */
private const val MAX_PHONE_CARD_WIDTH = 116f
