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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class TextFittingTest {

    @Test
    fun `the smaller step is tried first`() {
        assertEquals(listOf(0.975f, 0.95f), fitScalesFor(1f))
    }

    @Test
    fun `no scale sets the text below the minimum`() {
        assertEquals(emptyList(), fitScalesFor(UserPreferences.MIN_FONT_SCALE))
        assertEquals(listOf(0.975f), fitScalesFor(UserPreferences.MIN_FONT_SCALE / 0.97f))
    }

    @Test
    fun `the text is set smaller only where that takes a page away`() {
        assertEquals(1f, chooseFitScale(pageCount = 3, fitScales = listOf(0.975f, 0.95f)) { 3 })
        assertEquals(0.95f, chooseFitScale(pageCount = 3, fitScales = listOf(0.975f, 0.95f)) { if (it < 0.96f) 2 else 3 })
    }

    @Test
    fun `the first scale that saves a page wins`() {
        val tried = mutableListOf<Float>()
        assertEquals(0.975f, chooseFitScale(pageCount = 3, fitScales = listOf(0.975f, 0.95f)) { tried += it; 2 })
        assertEquals(listOf(0.975f), tried)
    }

    @Test
    fun `a scale that was not laid out saves nothing`() {
        assertEquals(1f, chooseFitScale(pageCount = 3, fitScales = listOf(0.975f, 0.95f)) { null })
    }
}
