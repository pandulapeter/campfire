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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_guitar_strings
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where song details' instrument card takes a press: its two halves fill the card edge to edge, under the round tuner
 * button too, whose own presses it keeps, so that a press anywhere on the card but the button opens the half's sheet,
 * in every form of the card (stacked on a phone and in the side panel, one line in a short window, with the tuner and
 * without it); and how much of a short window's one row it leaves the chord chips beside the tempo chip.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockInstrumentCardPressTest {

    @Test
    fun `on a phone a press near the card's end beside the tuner opens the instrument's and the tuning's sheets`() = runAppTest {
        openSong()
        waitForIdle()
        val tuner = tuner()
        val instrument = instrumentHalf()
        val tuning = tuningHalf()
        assertTrue(instrument.right >= tuner.right && tuning.right >= tuner.right, "The halves at $instrument and $tuning stop before the tuner at $tuner.")
        assertFillsCard(instrument, tuning, card = tempoChip().copy(left = instrument.left, right = instrument.right))
        pressNearEnd(instrumentHalfNode(), isTop = true)
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
        pressBack()
        waitUntil { viewModel.dialogHost.visibleDialog.value == null }
        pressNearEnd(tuningHalfNode(), isTop = false)
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuning }
        pressBack()
        waitUntil { viewModel.dialogHost.visibleDialog.value == null }
        // The round button over the halves still takes its own press.
        onNode(tunerNode()).performMouseInput {
            moveTo(center)
            click(center)
        }
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuner }
    }

    @Test
    fun `on a phone without the tuner the halves fill the card`() = runAppTest(
        extraPreferences = mapOf("isDockTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        val instrument = instrumentHalf()
        assertFillsCard(instrument, tuningHalf(), card = tempoChip().copy(left = instrument.left, right = instrument.right))
        pressNearEnd(instrumentHalfNode(), isTop = true)
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Instrument }
    }

    @Test
    fun `in the side panel the halves fill the card as wide as the tempo chip, the tuner's end too`() = runAppTest(window = TestWindow.DESKTOP) {
        openSong()
        waitForIdle()
        assertFillsCard(instrumentHalf(), tuningHalf(), card = tempoChip().copy(top = instrumentHalf().top, bottom = tuningHalf().bottom))
        assertTrue(instrumentHalf().right >= tuner().right, "The instrument half at ${instrumentHalf()} stops before the tuner at ${tuner()}.")
        pressNearEnd(tuningHalfNode(), isTop = false)
        waitUntil { viewModel.dialogHost.visibleDialog.value is DialogType.Tuning }
    }

    @Test
    fun `in the side panel without the tuner the halves fill the card`() = runAppTest(
        window = TestWindow.DESKTOP,
        extraPreferences = mapOf("isDockTunerEnabled" to JsonPrimitive(false)),
    ) {
        openSong()
        waitForIdle()
        assertFillsCard(instrumentHalf(), tuningHalf(), card = tempoChip().copy(top = instrumentHalf().top, bottom = tuningHalf().bottom))
    }

    @Test
    fun `in a short window's one-line card the instrument half runs to the card's start under the tuner`() = runAppTest(window = TestWindow.PHONE_ON_ITS_SIDE) {
        openSong()
        waitForIdle()
        val instrument = instrumentHalf()
        val tuning = tuningHalf()
        val tuner = tuner()
        val tempoChip = tempoChip()
        assertTrue(abs(instrument.left - tuner.left) <= SLACK, "The instrument half at $instrument does not reach the card's start at $tuner.")
        assertTrue(abs(instrument.top - tempoChip.top) <= SLACK && abs(instrument.bottom - tempoChip.bottom) <= SLACK, "The half at $instrument is not the card's height, $tempoChip.")
        assertTrue(abs(tuning.left - instrument.right) <= SLACK, "The halves at $instrument and $tuning leave a gap between them.")
    }

    @Test
    fun `a short narrow window's one-line card with the longest names leaves the chord chips room beside the tempo chip`() = runAppTest(
        window = TestWindow(width = 400.dp, height = 380.dp),
        extraPreferences = mapOf(
            "instrument" to JsonPrimitive(UserPreferences.Instrument.GUITAR_TWELVE_STRING.id),
            "tunings" to JsonObject(mapOf(UserPreferences.Instrument.GUITAR_TWELVE_STRING.id to JsonPrimitive("guitar_full_step_down"))),
        ),
    ) {
        openSong()
        waitForIdle()
        val instrument = onNode(hasText(string(Res.string.instrument_guitar_strings, 12)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        // The tuning half, a press of its own, starts where the instrument's ends and ends the card.
        val cardEnd = onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            .single { abs(it.left - instrument.right) <= SLACK && abs(it.top - instrument.top) <= SLACK }.right
        val tempoChip = tempoChip()
        assertTrue(instrument.left < cardEnd, "The card at $instrument is not where it should be.")
        val chips = onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.left >= cardEnd && it.right <= tempoChip.left }
        assertTrue(chips.size >= 2, "Only ${chips.size} chips fit between the card ending at $cardEnd and the tempo chip at $tempoChip.")
    }

    /** Asserts that the stacked halves [instrument] and [tuning] fill [card] edge to edge, as wide and as tall as it. */
    private fun assertFillsCard(instrument: Rect, tuning: Rect, card: Rect) {
        assertTrue(abs(instrument.left - card.left) <= SLACK && abs(instrument.right - card.right) <= SLACK, "The instrument half at $instrument is not as wide as the card at $card.")
        assertTrue(abs(tuning.left - card.left) <= SLACK && abs(tuning.right - card.right) <= SLACK, "The tuning half at $tuning is not as wide as the card at $card.")
        assertTrue(abs(instrument.top - card.top) <= SLACK && abs(tuning.bottom - card.bottom) <= SLACK, "The halves from ${instrument.top} to ${tuning.bottom} do not fill the card at $card.")
    }

    /**
     * Clicks [matcher]'s half 4 dp in from its end and 12 dp from its top or bottom: beside the round tuner, past its end,
     * within the card's rounded corner. With a mouse, whose pointer the button takes only over itself, since a touch
     * would be given to the button's 48 dp touch target, which reaches over most of that corner on a phone's card.
     */
    private fun AppTestScope.pressNearEnd(matcher: SemanticsMatcher, isTop: Boolean) {
        onNode(matcher).performMouseInput {
            val position = Offset(width - 4.dp.toPx(), if (isTop) 12.dp.toPx() else height - 12.dp.toPx())
            moveTo(position)
            click(position)
        }
        waitForIdle()
    }

    private fun AppTestScope.instrumentHalfNode() =
        hasText(string(Res.string.instrument_guitar)) and hasClickAction()

    private fun AppTestScope.tuningHalfNode() = hasText(string(Res.string.instrument_tuning_standard)) and hasClickAction()

    private fun AppTestScope.tunerNode() = hasContentDescription(string(Res.string.tuner)) and hasClickAction() and !hasAnyAncestor(isDialog())

    private fun AppTestScope.instrumentHalf(): Rect = onNode(instrumentHalfNode()).fetchSemanticsNode().boundsInRoot

    private fun AppTestScope.tuningHalf(): Rect = onNode(tuningHalfNode()).fetchSemanticsNode().boundsInRoot

    private fun AppTestScope.tuner(): Rect = onNode(tunerNode()).fetchSemanticsNode().boundsInRoot
}

/** A pixel either way, a dp being a pixel in the tests' window. */
private const val SLACK = 1.5f
