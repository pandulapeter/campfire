/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PagerIndicatorTest {

    @Test
    fun `an item is fully selected on its own page and not at all a page away`() {
        assertEquals(1f, pagerSelection(position = 2f, index = 2))
        assertEquals(0f, pagerSelection(position = 1f, index = 2))
        assertEquals(0f, pagerSelection(position = 3.5f, index = 2))
    }

    @Test
    fun `a turn hands the selection over as far as it has gone`() {
        assertEquals(0.75f, pagerSelection(position = 1.25f, index = 1))
        assertEquals(0.25f, pagerSelection(position = 1.25f, index = 2))
    }

    @Test
    fun `the tab indicator sits on the tab of the page shown`() {
        val spans = listOf(IndicatorSpan(0f, 40f), IndicatorSpan(60f, 80f))
        assertEquals(IndicatorSpan(60f, 80f), pagerIndicatorSpan(position = 1f, spans = spans))
    }

    @Test
    fun `the tab indicator moves and resizes between two tabs with the turn`() {
        val spans = listOf(IndicatorSpan(0f, 40f), IndicatorSpan(60f, 80f), IndicatorSpan(160f, 20f))
        assertEquals(IndicatorSpan(30f, 60f), pagerIndicatorSpan(position = 0.5f, spans = spans))
    }

    @Test
    fun `a position past either end stays on the end tab, and no tabs is no indicator`() {
        val spans = listOf(IndicatorSpan(0f, 40f), IndicatorSpan(60f, 80f))
        assertEquals(IndicatorSpan(0f, 40f), pagerIndicatorSpan(position = -0.2f, spans = spans))
        assertEquals(IndicatorSpan(60f, 80f), pagerIndicatorSpan(position = 1.3f, spans = spans))
        assertNull(pagerIndicatorSpan(position = 0f, spans = emptyList()))
    }
}
