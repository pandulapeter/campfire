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
    fun `the smallest supported phone keeps the cover in the performance bar without the key chip`() {
        assertEquals(buttons(cover = true, setlistAssignments = false), appBarButtons(360.dp, hasCover = true, isReadOnly = true, hasKeyChip = false))
    }

    @Test
    fun `the performance bar never holds the setlist assignments`() {
        widths.forEach { assertFalse(appBarButtons(it, hasCover = true, isReadOnly = true, hasKeyChip = true).isSetlistAssignmentsShown, "$it") }
    }

    @Test
    fun `a control of the bar that fits at one width fits at every wider one`() {
        listOf(true, false).forEach { isReadOnly ->
            listOf(true, false).forEach { hasCover ->
                listOf(true, false).forEach { hasKeyChip ->
                    widths.map { appBarButtons(it, hasCover = hasCover, isReadOnly = isReadOnly, hasKeyChip = hasKeyChip) }.zipWithNext().forEach { (narrower, wider) ->
                        if (narrower.isCoverShown) assertTrue(wider.isCoverShown)
                        if (narrower.isSetlistAssignmentsShown) assertTrue(wider.isSetlistAssignmentsShown)
                    }
                }
            }
        }
    }

    @Test
    fun `the setlist assignments leave the bar before the cover`() {
        listOf(true, false).forEach { hasCover ->
            val layouts = widths.map { appBarButtons(it, hasCover = hasCover, isReadOnly = false, hasKeyChip = true) }
            layouts.forEach { if (it.isSetlistAssignmentsShown) assertTrue(it.isCoverShown) }
            assertTrue(layouts.any { it.isCoverShown && !it.isSetlistAssignmentsShown }, "hasCover = $hasCover")
            assertEquals(buttons(cover = true, setlistAssignments = true), layouts.last())
        }
    }

    @Test
    fun `a pager without covers keeps the setlist assignments at narrower widths than one with them`() {
        val narrowestWithCovers = widths.first { appBarButtons(it, hasCover = true, isReadOnly = false, hasKeyChip = true).isSetlistAssignmentsShown }
        val narrowestWithoutCovers = widths.first { appBarButtons(it, hasCover = false, isReadOnly = false, hasKeyChip = true).isSetlistAssignmentsShown }
        assertTrue(narrowestWithoutCovers < narrowestWithCovers)
    }

    @Test
    fun `the smallest supported phone holds the key chip and the setlist assignments beside the title`() {
        assertTrue(appBarButtons(360.dp, hasCover = false, isReadOnly = false, hasKeyChip = true).isSetlistAssignmentsShown)
    }

    @Test
    fun `the key chip in the bar sends its other controls into the menu sooner`() {
        listOf(true, false).forEach { isReadOnly ->
            widths.forEach { width ->
                val without = appBarButtons(width, hasCover = true, isReadOnly = isReadOnly, hasKeyChip = false)
                val with = appBarButtons(width, hasCover = true, isReadOnly = isReadOnly, hasKeyChip = true)
                if (with.isCoverShown) assertTrue(without.isCoverShown)
                if (with.isSetlistAssignmentsShown) assertTrue(without.isSetlistAssignmentsShown)
            }
        }
    }

    private fun buttons(cover: Boolean, setlistAssignments: Boolean) = AppBarButtons(
        isCoverShown = cover,
        isSetlistAssignmentsShown = setlistAssignments,
    )
}
