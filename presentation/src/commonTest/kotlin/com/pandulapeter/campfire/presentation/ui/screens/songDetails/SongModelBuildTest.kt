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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongModelBuildTest {

    @Test
    fun `the page being read builds in place`() {
        assertTrue(buildsModelInPlace(page = 3, currentPage = 3, targetPage = 3, isVisible = true))
        assertTrue(buildsModelInPlace(page = 3, currentPage = 3, targetPage = 3, isVisible = false))
    }

    @Test
    fun `the page a swipe is headed for builds in place while it differs from the current one`() {
        assertTrue(buildsModelInPlace(page = 4, currentPage = 3, targetPage = 4, isVisible = false))
    }

    @Test
    fun `a neighbour sliding in at the edge builds in place before it is the target`() {
        assertTrue(buildsModelInPlace(page = 4, currentPage = 3, targetPage = 3, isVisible = true))
    }

    @Test
    fun `the page before the target builds in place, at rest and mid-swipe in either direction`() {
        assertTrue(buildsModelInPlace(page = 2, currentPage = 3, targetPage = 3, isVisible = false))
        assertTrue(buildsModelInPlace(page = 3, currentPage = 3, targetPage = 4, isVisible = false))
        assertTrue(buildsModelInPlace(page = 1, currentPage = 3, targetPage = 2, isVisible = false))
    }

    @Test
    fun `the composed page after the current one builds in the background while it is out of sight`() {
        assertFalse(buildsModelInPlace(page = 4, currentPage = 3, targetPage = 3, isVisible = false))
    }

    @Test
    fun `after a forward settle the page two ahead builds in the background`() {
        assertFalse(buildsModelInPlace(page = 5, currentPage = 4, targetPage = 4, isVisible = false))
    }
}
