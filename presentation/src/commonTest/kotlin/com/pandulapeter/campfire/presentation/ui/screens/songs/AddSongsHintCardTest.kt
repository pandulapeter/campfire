/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddSongsHintCardTest {

    @Test
    fun `the hint is shown under the demo library`() {
        assertTrue(isShown(librarySongCount = 2))
        assertTrue(isShown(librarySongCount = 1))
    }

    @Test
    fun `the hint leaves once the library holds three songs`() {
        assertFalse(isShown(librarySongCount = 3))
    }

    @Test
    fun `the hint waits for the library to be read`() {
        assertFalse(isShown(librarySongCount = null))
    }

    @Test
    fun `the hint is not shown with a placeholder, a search, the selection, or performance mode`() {
        assertFalse(isShown(hasPlaceholder = true))
        assertFalse(isShown(isSearchOpen = true))
        assertFalse(isShown(isSelecting = true))
        assertFalse(isShown(isPerformanceModeEnabled = true))
    }

    private fun isShown(
        librarySongCount: Int? = 2,
        hasPlaceholder: Boolean = false,
        isSearchOpen: Boolean = false,
        isSelecting: Boolean = false,
        isPerformanceModeEnabled: Boolean = false,
    ) = isAddSongsHintShown(
        librarySongCount = librarySongCount,
        hasPlaceholder = hasPlaceholder,
        isSearchOpen = isSearchOpen,
        isSelecting = isSelecting,
        isPerformanceModeEnabled = isPerformanceModeEnabled,
    )
}
