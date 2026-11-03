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
import com.pandulapeter.campfire.domain.api.library.SongSection
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroupHeader
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SortChangeRevealTest {

    private val reveal = SortChangeReveal()

    private val ledByPicks = listOf(
        SongGroup(header = SongGroupHeader.RandomPicks, songs = emptyList()),
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('A')), songs = emptyList()),
    )

    private fun songGroups(orderKey: String, groups: List<SongGroup> = ledByPicks) = SongGroups(
        filterKey = "",
        orderKey = orderKey,
        groups = groups,
        isLibraryRead = true,
        areRandomPicksAwaited = false,
        librarySongCount = 0,
        filteredSongCount = 0,
    )

    @Test
    fun `a sort picked over the very top is revealed once, when the new order arrives`() {
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_ARTIST"), isRevealed = true, isReselected = false)
        assertFalse(reveal.takeGlide("|BY_ARTIST"), "The old order, still")
        assertTrue(reveal.takeGlide("|BY_TITLE"))
        assertFalse(reveal.takeGlide("|BY_ARTIST"))
    }

    @Test
    fun `a sort picked further down the list lands straight on the new first section`() {
        val groups = ledByPicks
        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = 7), songGroups("|BY_TITLE"), isRevealed = true, isReselected = false)
        assertEquals(0, reveal.topIndex("|BY_TITLE", groups), "The old order, still")
        assertFalse(reveal.takeGlide("|BY_ARTIST"), "Not glided to: put there")
        assertEquals(groups.firstSectionAfterRandomPicksIndex(), reveal.topIndex("|BY_ARTIST", groups))
        assertEquals(0, reveal.topIndex("|BY_TITLE", groups), "Asked once")
    }

    @Test
    fun `a sort under an open search is not revealed`() {
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_ARTIST"), isRevealed = false, isReselected = false)
        assertFalse(reveal.takeGlide("|BY_TITLE"))
        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = 3), songGroups("|BY_ARTIST"), isRevealed = false, isReselected = false)
        assertEquals(0, reveal.topIndex("|BY_TITLE", ledByPicks))
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_ARTIST"), isRevealed = false, isReselected = true)
        assertNull(reveal.reselection)
    }

    @Test
    fun `the sort already applied, picked again while the picks are in sight, glides to the first section at once`() {
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_TITLE"), isRevealed = true, isReselected = true)
        val first = assertNotNull(reveal.reselection)
        assertEquals(ledByPicks.firstSectionAfterRandomPicksIndex(), first.index)
        assertFalse(reveal.takeGlide("|BY_ARTIST"), "No new order to wait for")
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_TITLE"), isRevealed = true, isReselected = true)
        assertNotSame(first, reveal.reselection, "Every pick glides")
        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = first.index - 1, firstVisibleItemScrollOffset = 40), songGroups("|BY_TITLE"), isRevealed = true, isReselected = true)
        assertEquals(first.index, reveal.reselection?.index, "Scrolled, the picks still in sight")
    }

    @Test
    fun `the sort already applied, picked again in the sections or without picks, moves nothing`() {
        val firstSection = assertNotNull(ledByPicks.firstSectionAfterRandomPicksIndex())
        reveal.onSortPicked(LazyGridState(firstVisibleItemIndex = firstSection), songGroups("|BY_TITLE"), isRevealed = true, isReselected = true)
        assertNull(reveal.reselection)
        val withoutPicks = listOf(SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('A')), songs = emptyList()))
        reveal.onSortPicked(LazyGridState(), songGroups("|BY_TITLE", withoutPicks), isRevealed = true, isReselected = true)
        assertNull(reveal.reselection)
    }
}
