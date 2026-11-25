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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openPlayingSheet
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Song details' key chip and tuner in the app bar, where the dock would hold nothing else. */
@OptIn(ExperimentalTestApi::class)
internal class AppBarKeyChipTest {

    @Test
    fun `the key chip and the tuner are the app bar's where the dock would hold nothing else`() = runAppTest(
        extraPreferences = KEY_ONLY_DOCK,
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

    @Test
    fun `the app bar's key chip is as tall as the text size stepper before it and as far from it as from the tuner`() = runAppTest(
        window = TestWindow(width = 800.dp, height = 640.dp),
        extraPreferences = KEY_ONLY_DOCK,
    ) {
        openSong()
        waitForIdle()
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot
        val stepperEnd = onNode(hasContentDescription(string(Res.string.song_details_text_size_increase)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot
        val tuner = onNode(hasContentDescription(string(Res.string.tuner)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(STEPPER_HEIGHT.value, keyChip.height, 0.5f, "The key chip at $keyChip is not as tall as the text size stepper.")
        assertEquals(stepperEnd.center.y, keyChip.center.y, 0.5f, "The key chip at $keyChip is not centred beside the stepper's $stepperEnd.")
        assertEquals(tuner.center.y, keyChip.center.y, 0.5f, "The key chip at $keyChip is not centred beside the tuner at $tuner.")
        // The tuner's glyph keeps the gap clear inside its touch target, so the chip is as far from the glyph as from the stepper.
        val tunerGlyphGap = (tuner.width - TUNER_GLYPH_SIZE) / 2
        assertEquals(keyChip.left - stepperEnd.right, tuner.left + tunerGlyphGap - keyChip.right, 0.5f, "The key chip at $keyChip is not evenly spaced.")
    }
}

/** The switches that leave the dock only the key chip, which the app bar then holds. */
private val KEY_ONLY_DOCK = mapOf(
    "isDockMetronomeEnabled" to JsonPrimitive(false),
    "areChordDiagramsEnabled" to JsonPrimitive(false),
    "areScaleSuggestionsEnabled" to JsonPrimitive(false),
)

/** An icon button's glyph, in dp. */
private const val TUNER_GLYPH_SIZE = 24f

/** Under the status bar and the app bar of the test's phone, and well above any song line under them, in dp. */
private const val APP_BAR_BOTTOM = 120f
