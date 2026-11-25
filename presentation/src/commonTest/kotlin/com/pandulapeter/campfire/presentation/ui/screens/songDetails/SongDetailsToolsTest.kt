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
    fun `with every switch on the dock holds the click and the tuner`() =
        assertEquals(SongDetailsTools(isMetronomeEnabled = true, isTunerEnabled = true, isTunerInMenu = false), songDetailsToolsOf(testPreferences(), shouldShowChords = true))

    @Test
    fun `the dock's own switches take the click and the tuner off the song while the tabs keep them`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = false, isTunerEnabled = false, isTunerInMenu = true),
        songDetailsToolsOf(testPreferences().copy(isDockMetronomeEnabled = false, isDockTunerEnabled = false), shouldShowChords = true),
    )

    @Test
    fun `with neither the chords nor the click there is no dock so the menu holds the tuner`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = false, isTunerEnabled = true, isTunerInMenu = true),
        songDetailsToolsOf(testPreferences().copy(isMetronomeEnabled = false), shouldShowChords = false),
    )

    @Test
    fun `with the tuner feature off no tuner is offered anywhere`() = assertEquals(
        SongDetailsTools(isMetronomeEnabled = true, isTunerEnabled = false, isTunerInMenu = false),
        songDetailsToolsOf(testPreferences().copy(isTunerEnabled = false), shouldShowChords = true),
    )
}
