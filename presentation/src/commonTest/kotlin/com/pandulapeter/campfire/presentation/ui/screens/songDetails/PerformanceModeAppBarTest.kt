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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceModeAppBarTest {

    @Test
    fun `a phone in portrait leaves the cover out of the bar`() {
        assertFalse(showsCoverInPerformanceMode(360.dp))
    }

    @Test
    fun `the cover stays for as long as the title keeps 160dp beside it`() {
        // The back button and the bar's paddings, the stepper and its padding, and the cover and its gap take 232dp.
        assertTrue(showsCoverInPerformanceMode(392.dp))
        assertFalse(showsCoverInPerformanceMode(391.dp))
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
    fun `a phone in portrait keeps the text size stepper in the bar`() {
        assertTrue(showsFontScaleInPerformanceBar(360.dp))
    }

    @Test
    fun `the transposition stepper is in the bar for as long as the title keeps 280dp beside it`() {
        // Beside 152dp of everything else, the stepper and its padding take 148dp.
        assertTrue(showsTranspositionInBar(appBarWidth = 580.dp, otherContentWidth = 152.dp))
        assertFalse(showsTranspositionInBar(appBarWidth = 579.dp, otherContentWidth = 152.dp))
    }

    @Test
    fun `a phone keeps the transposition stepper in the menu`() {
        assertFalse(showsTranspositionInBar(appBarWidth = 411.dp, otherContentWidth = 152.dp))
    }
}
