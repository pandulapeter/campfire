/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class EscapeStepTest {

    @Test
    fun `the reorder mode ends before anything else`() {
        assertEquals(EscapeStep.END_REORDERING, escapeStepOf(isReordering = true, isSearchOpen = true, isSelecting = true, stackSize = 3, isFilterShown = true))
    }

    @Test
    fun `an open search closes before the selection ends`() {
        assertEquals(EscapeStep.CLOSE_SEARCH, escapeStepOf(isReordering = false, isSearchOpen = true, isSelecting = true, stackSize = 1, isFilterShown = true))
    }

    @Test
    fun `the selection ends before a screen is left`() {
        assertEquals(EscapeStep.END_SELECTION, escapeStepOf(isReordering = false, isSearchOpen = false, isSelecting = true, stackSize = 2, isFilterShown = true))
    }

    @Test
    fun `a screen with another under it is left`() {
        assertEquals(EscapeStep.GO_BACK, escapeStepOf(isReordering = false, isSearchOpen = false, isSelecting = false, stackSize = 2, isFilterShown = true))
    }

    @Test
    fun `the filters of the last screen are reset before the app is closed`() {
        assertEquals(EscapeStep.CLEAR_FILTERS, escapeStepOf(isReordering = false, isSearchOpen = false, isSelecting = false, stackSize = 1, isFilterShown = true))
    }

    @Test
    fun `the last screen asks to close the app`() {
        assertEquals(EscapeStep.EXIT, escapeStepOf(isReordering = false, isSearchOpen = false, isSelecting = false, stackSize = 1, isFilterShown = false))
    }
}
