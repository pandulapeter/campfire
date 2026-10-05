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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceModeAppBarTest {

    @Test
    fun `a phone in portrait leaves the cover out of the bar`() {
        assertFalse(showsCoverInPerformanceMode(360.dp))
    }

    @Test
    fun `the cover stays for as long as the title keeps 160dp beside it`() {
        // The back button and the bar's paddings, the stepper and its padding, the metronome button, and the cover and
        // its gap take 280dp.
        assertTrue(showsCoverInPerformanceMode(440.dp))
        assertFalse(showsCoverInPerformanceMode(439.dp))
    }

    @Test
    fun `a tablet keeps the cover`() {
        assertTrue(showsCoverInPerformanceMode(800.dp))
    }

    @Test
    fun `the text size stepper stays in the bar for as long as the title keeps 160dp beside it`() {
        // The back button and the bar's paddings, and the stepper and its padding take 180dp.
        assertTrue(showsFontScaleInPerformanceBar(340.dp))
        assertFalse(showsFontScaleInPerformanceBar(339.dp))
    }

    @Test
    fun `the metronome button goes into the menu before the text size stepper does`() {
        assertTrue(showsMetronomeInPerformanceBar(388.dp))
        assertFalse(showsMetronomeInPerformanceBar(387.dp))
        assertTrue(showsFontScaleInPerformanceBar(387.dp))
    }

    @Test
    fun `a phone in portrait keeps the text size stepper in the bar`() {
        assertTrue(showsFontScaleInPerformanceBar(360.dp))
    }

    @Test
    fun `the transposition stepper is in the bar for as long as the title keeps 280dp beside it`() {
        // Beside 104dp of everything else and the three buttons, the stepper takes 140dp.
        assertTrue(appBarButtons(appBarWidth = 668.dp, otherContentWidth = 104.dp).isTranspositionShown)
        assertFalse(appBarButtons(appBarWidth = 667.dp, otherContentWidth = 104.dp).isTranspositionShown)
    }

    @Test
    fun `the setlist assignments leave the bar before the song info, and the song info before the metronome`() {
        assertEquals(buttons(metronome = true, songInfo = true, setlistAssignments = true), appBarButtons(408.dp, 104.dp))
        assertEquals(buttons(metronome = true, songInfo = true, setlistAssignments = false), appBarButtons(407.dp, 104.dp))
        assertEquals(buttons(metronome = true, songInfo = true, setlistAssignments = false), appBarButtons(360.dp, 104.dp))
        assertEquals(buttons(metronome = true, songInfo = false, setlistAssignments = false), appBarButtons(359.dp, 104.dp))
        assertEquals(buttons(metronome = true, songInfo = false, setlistAssignments = false), appBarButtons(312.dp, 104.dp))
        assertEquals(buttons(metronome = false, songInfo = false, setlistAssignments = false), appBarButtons(311.dp, 104.dp))
    }

    @Test
    fun `a phone keeps the three buttons and puts the transposition stepper in the menu`() {
        assertEquals(buttons(metronome = true, songInfo = true, setlistAssignments = true), appBarButtons(411.dp, 104.dp))
    }

    private fun buttons(metronome: Boolean, songInfo: Boolean, setlistAssignments: Boolean) = AppBarButtons(
        isMetronomeShown = metronome,
        isSongInfoShown = songInfo,
        isSetlistAssignmentsShown = setlistAssignments,
        isTranspositionShown = false,
    )
}
