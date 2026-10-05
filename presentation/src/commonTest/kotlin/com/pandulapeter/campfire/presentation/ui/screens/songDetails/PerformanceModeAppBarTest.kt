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
    fun `the setlist assignments leave the bar before the cover`() {
        // The back button, the bar's paddings, the metronome and the two menus take 176dp, the cover 52dp more, and
        // the setlist assignments 36dp.
        assertEquals(buttons(cover = true, setlistAssignments = true), appBarButtons(424.dp, hasCover = true))
        assertEquals(buttons(cover = true, setlistAssignments = false), appBarButtons(423.dp, hasCover = true))
        assertEquals(buttons(cover = true, setlistAssignments = false), appBarButtons(388.dp, hasCover = true))
        assertEquals(buttons(cover = false, setlistAssignments = false), appBarButtons(387.dp, hasCover = true))
    }

    @Test
    fun `a pager without covers keeps no room for one`() {
        assertEquals(buttons(cover = true, setlistAssignments = true), appBarButtons(372.dp, hasCover = false))
        assertEquals(buttons(cover = true, setlistAssignments = false), appBarButtons(371.dp, hasCover = false))
    }

    @Test
    fun `the smallest supported phone still leaves the title its room`() {
        assertEquals(buttons(cover = false, setlistAssignments = false), appBarButtons(360.dp, hasCover = true))
    }

    private fun buttons(cover: Boolean, setlistAssignments: Boolean) = AppBarButtons(
        isCoverShown = cover,
        isSetlistAssignmentsShown = setlistAssignments,
    )
}
