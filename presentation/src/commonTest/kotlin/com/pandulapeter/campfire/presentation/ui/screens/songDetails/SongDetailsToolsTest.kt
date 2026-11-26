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

import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SongDetailsToolsTest {

    @Test
    fun `with every switch on the dock holds the click and the instrument card the tuner`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = true, isTunerEnabled = true, isTunerInCard = true),
        songDetailsToolsOf(testPreferences(), isImportPreview = false),
    )

    @Test
    fun `the dock's own switch takes the click off the song while the tab keeps it`() =
        assertFalse(songDetailsToolsOf(testPreferences().copy(isDockMetronomeEnabled = false), isImportPreview = false).isMetronomeEnabled)

    @Test
    fun `with the dock's tuner on the menu holds the tuner only where the instrument card is not shown`() {
        val tools = songDetailsToolsOf(testPreferences(), isImportPreview = false)
        assertFalse(tools.isTunerInMenu(hasInstrumentCard = true))
        assertTrue(tools.isTunerInMenu(hasInstrumentCard = false))
    }

    @Test
    fun `with the dock's tuner off the menu holds the tuner and the card does not`() {
        val tools = songDetailsToolsOf(testPreferences().copy(isDockTunerEnabled = false), isImportPreview = false)
        assertFalse(tools.isTunerInCard)
        assertTrue(tools.isTunerInMenu(hasInstrumentCard = true))
        assertTrue(tools.isTunerInMenu(hasInstrumentCard = false))
    }

    @Test
    fun `with the tuner feature off no tuner is offered whatever the dock's switch says`() {
        val tools = songDetailsToolsOf(testPreferences().copy(isTunerEnabled = false, isDockTunerEnabled = true), isImportPreview = false)
        assertEquals(SongDetailsTools(isMetronomeEnabled = true, isTunerEnabled = false, isTunerInCard = false), tools)
        assertFalse(tools.isTunerInMenu(hasInstrumentCard = true))
        assertFalse(tools.isTunerInMenu(hasInstrumentCard = false))
    }

    @Test
    fun `an import preview offers no tuner`() {
        val tools = songDetailsToolsOf(testPreferences(), isImportPreview = true)
        assertFalse(tools.isTunerEnabled || tools.isTunerInCard || tools.isTunerInMenu(hasInstrumentCard = false))
    }
}
