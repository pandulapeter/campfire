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

import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SortChangeRevealTest {

    private val reveal = SortChangeReveal()

    @Test
    fun `a sort picked over the very top is revealed once, when the new order arrives`() {
        reveal.onSortPicked(LazyGridState(), orderKey = "|BY_ARTIST", isRevealed = true)
        assertFalse(reveal.takeGlide("|BY_ARTIST"), "The old order, still")
        assertTrue(reveal.takeGlide("|BY_TITLE"))
        assertFalse(reveal.takeGlide("|BY_ARTIST"))
    }

    @Test
    fun `a sort picked over a list resting where the last reveal left it lands on the new first section`() {
        val groups = listOf(
            SongGroup(header = SongGroupHeader.RandomPicks, songs = emptyList()),
            SongGroup(header = SongGroupHeader.Selection, songs = emptyList()),
        )
        reveal.onSortPicked(LazyGridState(), orderKey = "|BY_ARTIST", isRevealed = true)
        reveal.takeGlide("|BY_TITLE")
        reveal.onGlided(LazyGridState(firstVisibleItemIndex = 7))

        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = 7), orderKey = "|BY_TITLE", isRevealed = true)
        assertEquals(0, reveal.topIndex("|BY_TITLE", groups), "The old order, still")
        assertFalse(reveal.takeGlide("|BY_ARTIST"), "Not glided to: already there")
        assertEquals(groups.firstSectionAfterRandomPicksIndex(), reveal.topIndex("|BY_ARTIST", groups))

        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = 5), orderKey = "|BY_ARTIST", isRevealed = true)
        assertEquals(0, reveal.topIndex("|BY_TITLE", groups), "Scrolled since")
    }

    @Test
    fun `a sort picked further down the list, or one that changes nothing, is not revealed`() {
        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = 3), orderKey = "|BY_ARTIST", isRevealed = true)
        assertFalse(reveal.takeGlide("|BY_TITLE"))
        reveal.onSortPicked(LazyGridState(), orderKey = "|BY_ARTIST", isRevealed = false)
        assertFalse(reveal.takeGlide("|BY_TITLE"))
    }
}
