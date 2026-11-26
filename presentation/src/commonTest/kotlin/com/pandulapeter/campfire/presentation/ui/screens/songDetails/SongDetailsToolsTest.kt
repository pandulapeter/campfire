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

internal class SongDetailsToolsTest {

    @Test
    fun `with every switch on the dock holds the click and the menu the tuner`() =
        assertEquals(SongDetailsTools(isMetronomeEnabled = true, isTunerInMenu = true), songDetailsToolsOf(testPreferences(), isImportPreview = false))

    @Test
    fun `the dock's own switch takes the click off the song while the tab keeps it`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = false, isTunerInMenu = true),
        songDetailsToolsOf(testPreferences().copy(isDockMetronomeEnabled = false), isImportPreview = false),
    )

    @Test
    fun `with the tuner feature off no tuner is offered`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = true, isTunerInMenu = false),
        songDetailsToolsOf(testPreferences().copy(isTunerEnabled = false), isImportPreview = false),
    )

    @Test
    fun `an import preview offers no tuner`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = true, isTunerInMenu = false),
        songDetailsToolsOf(testPreferences(), isImportPreview = true),
    )
}
