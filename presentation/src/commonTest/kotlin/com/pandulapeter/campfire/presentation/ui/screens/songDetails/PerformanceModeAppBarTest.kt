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
    fun `the smallest supported phone keeps the cover in the performance bar but leaves the text size to the menu`() {
        assertEquals(buttons(cover = true, setlistAssignments = false, fontScale = false), appBarButtons(360.dp, hasCover = true, isReadOnly = true))
    }

    @Test
    fun `a large phone leaves the text size to the menu rather than squeeze the title and the cover`() {
        listOf(true, false).forEach { isReadOnly ->
            assertFalse(appBarButtons(448.dp, hasCover = true, isReadOnly = isReadOnly).isFontScaleShown, "isReadOnly = $isReadOnly")
        }
    }

    @Test
    fun `a tablet keeps every control of the performance bar`() {
        assertEquals(buttons(cover = true, setlistAssignments = false, fontScale = true), appBarButtons(800.dp, hasCover = true, isReadOnly = true))
    }

    @Test
    fun `the text size stepper leaves the bar before the cover in both modes`() {
        listOf(true, false).forEach { isReadOnly ->
            val layouts = widths.map { appBarButtons(it, hasCover = true, isReadOnly = isReadOnly) }
            layouts.forEach { if (it.isFontScaleShown) assertTrue(it.isCoverShown) }
            assertTrue(layouts.any { it.isCoverShown && !it.isFontScaleShown }, "isReadOnly = $isReadOnly")
        }
    }

    @Test
    fun `the performance bar never holds the setlist assignments`() {
        widths.forEach { assertFalse(appBarButtons(it, hasCover = true, isReadOnly = true).isSetlistAssignmentsShown, "$it") }
    }

    @Test
    fun `a control of the bar that fits at one width fits at every wider one`() {
        listOf(true, false).forEach { isReadOnly ->
            listOf(true, false).forEach { hasCover ->
                widths.map { appBarButtons(it, hasCover = hasCover, isReadOnly = isReadOnly) }.zipWithNext().forEach { (narrower, wider) ->
                    if (narrower.isCoverShown) assertTrue(wider.isCoverShown)
                    if (narrower.isSetlistAssignmentsShown) assertTrue(wider.isSetlistAssignmentsShown)
                    if (narrower.isFontScaleShown) assertTrue(wider.isFontScaleShown)
                }
            }
        }
    }

    @Test
    fun `the setlist assignments leave the bar before the cover`() {
        listOf(true, false).forEach { hasCover ->
            val layouts = widths.map { appBarButtons(it, hasCover = hasCover, isReadOnly = false) }
            layouts.forEach { if (it.isSetlistAssignmentsShown) assertTrue(it.isCoverShown) }
            assertTrue(layouts.any { it.isCoverShown && !it.isSetlistAssignmentsShown }, "hasCover = $hasCover")
            assertEquals(buttons(cover = true, setlistAssignments = true, fontScale = true), layouts.last())
        }
    }

    @Test
    fun `the text size leaves the bar before the setlist assignments`() {
        listOf(true, false).forEach { hasCover ->
            val layouts = widths.map { appBarButtons(it, hasCover = hasCover, isReadOnly = false) }
            layouts.forEach { if (it.isFontScaleShown) assertTrue(it.isSetlistAssignmentsShown) }
            assertTrue(layouts.any { it.isSetlistAssignmentsShown && !it.isFontScaleShown }, "hasCover = $hasCover")
        }
    }

    @Test
    fun `a pager without covers keeps the setlist assignments at narrower widths than one with them`() {
        val narrowestWithCovers = widths.first { appBarButtons(it, hasCover = true, isReadOnly = false).isSetlistAssignmentsShown }
        val narrowestWithoutCovers = widths.first { appBarButtons(it, hasCover = false, isReadOnly = false).isSetlistAssignmentsShown }
        assertTrue(narrowestWithoutCovers < narrowestWithCovers)
    }

    @Test
    fun `the smallest supported phone keeps the cover and the setlist assignments but leaves the text size to the menu`() {
        assertEquals(buttons(cover = true, setlistAssignments = true, fontScale = false), appBarButtons(360.dp, hasCover = true, isReadOnly = false))
    }

    @Test
    fun `a tablet has the text size in the bar`() {
        assertTrue(appBarButtons(800.dp, hasCover = true, isReadOnly = false).isFontScaleShown)
    }

    private fun buttons(cover: Boolean, setlistAssignments: Boolean, fontScale: Boolean) = AppBarButtons(
        isCoverShown = cover,
        isSetlistAssignmentsShown = setlistAssignments,
        isFontScaleShown = fontScale,
    )
}
