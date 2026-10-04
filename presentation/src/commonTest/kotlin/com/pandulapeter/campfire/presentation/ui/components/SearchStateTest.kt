/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchStateTest {

    @Test
    fun `an opening owes the focus once`() {
        val searchState = SearchState()
        searchState.open()
        assertTrue(searchState.takeFocusOnOpen())
        assertFalse(searchState.takeFocusOnOpen())
    }

    @Test
    fun `reopening a closed search owes the focus again`() {
        val searchState = SearchState()
        searchState.open()
        searchState.takeFocusOnOpen()
        searchState.close()
        searchState.reopen()
        assertTrue(searchState.takeFocusOnOpen())
    }

    @Test
    fun `a search restored open owes nothing`() {
        assertFalse(SearchState(isInitiallyOpen = true).takeFocusOnOpen())
    }
}
