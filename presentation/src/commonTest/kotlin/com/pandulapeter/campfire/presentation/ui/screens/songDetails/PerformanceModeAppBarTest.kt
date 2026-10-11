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

    /** Every width from below the smallest supported phone to a tablet, one dp apart. */
    private val widths = (240..900).map { it.dp }

    @Test
    fun `the smallest supported phone keeps the text size stepper but leaves the cover out of the performance bar`() {
        assertTrue(showsFontScaleInPerformanceBar(360.dp))
        assertFalse(showsCoverInPerformanceMode(360.dp))
    }

    @Test
    fun `a tablet keeps every control of the performance bar`() {
        assertTrue(showsCoverInPerformanceMode(800.dp))
        assertTrue(showsFontScaleInPerformanceBar(800.dp))
    }

    @Test
    fun `the performance bar loses the cover before the text size stepper`() {
        widths.forEach { width ->
            if (showsCoverInPerformanceMode(width)) assertTrue(showsFontScaleInPerformanceBar(width), "$width")
        }
        assertTrue(widths.any { showsFontScaleInPerformanceBar(it) && !showsCoverInPerformanceMode(it) })
    }

    @Test
    fun `a control of the performance bar that fits at one width fits at every wider one`() {
        listOf(::showsCoverInPerformanceMode, ::showsFontScaleInPerformanceBar).forEach { shows ->
            widths.zipWithNext().forEach { (narrower, wider) ->
                if (shows(narrower)) assertTrue(shows(wider), "$narrower, $wider")
            }
        }
    }

    @Test
    fun `the setlist assignments leave the bar before the cover`() {
        listOf(true, false).forEach { hasCover ->
            val layouts = widths.map { appBarButtons(it, hasCover = hasCover) }
            layouts.forEach { if (it.isSetlistAssignmentsShown) assertTrue(it.isCoverShown) }
            assertTrue(layouts.any { it.isCoverShown && !it.isSetlistAssignmentsShown }, "hasCover = $hasCover")
            assertEquals(buttons(cover = true, setlistAssignments = true, fontScale = true), layouts.last())
        }
    }

    @Test
    fun `the text size leaves the bar before the setlist assignments`() {
        listOf(true, false).forEach { hasCover ->
            val layouts = widths.map { appBarButtons(it, hasCover = hasCover) }
            layouts.forEach { if (it.isFontScaleShown) assertTrue(it.isSetlistAssignmentsShown) }
            assertTrue(layouts.any { it.isSetlistAssignmentsShown && !it.isFontScaleShown }, "hasCover = $hasCover")
        }
    }

    @Test
    fun `a pager without covers keeps the setlist assignments at narrower widths than one with them`() {
        val narrowestWithCovers = widths.first { appBarButtons(it, hasCover = true).isSetlistAssignmentsShown }
        val narrowestWithoutCovers = widths.first { appBarButtons(it, hasCover = false).isSetlistAssignmentsShown }
        assertTrue(narrowestWithoutCovers < narrowestWithCovers)
    }

    @Test
    fun `the smallest supported phone keeps the cover and the setlist assignments but leaves the text size to the menu`() {
        assertEquals(buttons(cover = true, setlistAssignments = true, fontScale = false), appBarButtons(360.dp, hasCover = true))
    }

    @Test
    fun `a tablet has the text size in the bar`() {
        assertTrue(appBarButtons(800.dp, hasCover = true).isFontScaleShown)
    }

    private fun buttons(cover: Boolean, setlistAssignments: Boolean, fontScale: Boolean) = AppBarButtons(
        isCoverShown = cover,
        isSetlistAssignmentsShown = setlistAssignments,
        isFontScaleShown = fontScale,
    )
}
