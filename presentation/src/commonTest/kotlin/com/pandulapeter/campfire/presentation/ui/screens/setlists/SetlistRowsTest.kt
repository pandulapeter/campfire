/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetlistRowsTest {

    private val first = setlistWithSongs("first", description = "", "a", "b", "c")
    private val second = setlistWithSongs("second", description = "For the encore", "d")

    @Test
    fun `without a drag the rows are the entries`() = assertEquals(
        first.entries.map { SetlistRow(entry = it, index = it.index) },
        first.rows(dragOrder = null),
    )

    @Test
    fun `a drag renumbers the rows by the slot they now sit in`() {
        val rows = first.rows(dragOrder = listOf("b", "a", "c"))
        assertEquals(listOf("b", "a", "c"), rows.map { it.entry.songFileName })
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
    }

    @Test
    fun `a dragged name the setlist no longer has is left out`() =
        assertEquals(listOf("c", "a"), first.rows(dragOrder = listOf("c", "gone", "a")).map { it.entry.songFileName })

    @Test
    fun `a header counts the headers entries and summaries before it`() {
        val setlists = listOf(second, first)
        assertEquals(0, setlists.headerIndexOf("second.setlist.json"))
        assertEquals(4, setlists.headerIndexOf("first.setlist.json"))
        assertEquals(5, listOf(first, second).headerIndexOf("second.setlist.json"))
        assertNull(setlists.headerIndexOf("third.setlist.json"))
        assertNull(setlists.headerIndexOf(null))
    }

    @Test
    fun `a countdown counts as a summary before a header even without a description`() {
        val counted = setlistWithSongs("counted", description = "", "a", "b", "c", isCountdownShown = true)
        assertEquals(6, listOf(counted, second).headerIndexOf("second.setlist.json"))
    }

    @Test
    fun `a row moves one place either way`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedOnePlace(from = 0, by = 1))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").movedOnePlace(from = 2, by = -1))
    }

    @Test
    fun `only a setlist in use with two songs or more can be reordered`() {
        assertTrue(first.canReorder)
        assertFalse(second.canReorder)
        assertFalse(first.copy(setlist = first.setlist.copy(isArchived = true)).canReorder)
    }

    @Test
    fun `a move within a setlist puts the song where it was dropped`() {
        val moved = null.afterMove(from = key("first", "a"), to = key("first", "c"), setlists = listOf(first, second))

        assertEquals(DraggedSetlist(setlistFileName = "first.setlist.json", songFileNames = listOf("b", "c", "a")), moved)
    }

    @Test
    fun `a move continues from the order the drag has put the setlist in`() {
        val dragged = DraggedSetlist(setlistFileName = "first.setlist.json", songFileNames = listOf("c", "b", "a"))

        assertEquals(listOf("b", "c", "a"), dragged.afterMove(from = key("first", "c"), to = key("first", "b"), setlists = listOf(first))?.songFileNames)
    }

    @Test
    fun `a move onto another setlist or onto no song is no move`() {
        assertNull(null.afterMove(from = key("first", "a"), to = key("second", "d"), setlists = listOf(first, second)))
        assertNull(null.afterMove(from = key("first", "a"), to = SetlistItemKey("setlist_first.setlist.json"), setlists = listOf(first)))
    }

    @Test
    fun `a drag settles once the library has its order`() {
        val dragged = DraggedSetlist(setlistFileName = "first.setlist.json", songFileNames = listOf("a", "b", "c"))

        assertTrue(dragged.isSettledBy(first))
        assertFalse(dragged.copy(songFileNames = listOf("b", "a", "c")).isSettledBy(first))
    }

    @Test
    fun `a drag is given up for a setlist that changed its songs, went, or was archived`() {
        val dragged = DraggedSetlist(setlistFileName = "first.setlist.json", songFileNames = listOf("b", "a"))

        assertTrue(dragged.isSettledBy(first))
        assertTrue(dragged.isSettledBy(null))
        assertTrue(dragged.copy(songFileNames = listOf("b", "a", "c")).isSettledBy(first.copy(setlist = first.setlist.copy(isArchived = true))))
    }

    private fun key(setlist: String, song: String) = SetlistItemKey(setlistFileName = "$setlist.setlist.json", songFileName = song)

    private fun setlistWithSongs(
        name: String,
        description: String,
        vararg songFileNames: String,
        isCountdownShown: Boolean = false,
    ) = SetlistWithSongs(
        setlist = Setlist(
            fileName = "$name.setlist.json",
            title = name,
            description = description,
            date = LocalDate(2026, 1, 1),
            isCountdownShown = isCountdownShown,
            isArchived = false,
            entries = songFileNames.map { Setlist.Entry(songFileName = it) },
            size = 0,
        ),
        entries = songFileNames.mapIndexed { index, songFileName -> SetlistWithSongs.Entry.Missing(index = index, songFileName = songFileName) },
    )
}
