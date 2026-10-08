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

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `a header counts the headers entries and descriptions before it`() {
        val setlists = listOf(second, first)
        assertEquals(0, setlists.headerIndexOf("second.setlist.json"))
        assertEquals(4, setlists.headerIndexOf("first.setlist.json"))
        assertEquals(5, listOf(first, second).headerIndexOf("second.setlist.json"))
        assertNull(setlists.headerIndexOf("third.setlist.json"))
        assertNull(setlists.headerIndexOf(null))
    }

    @Test
    fun `a row moves one place either way`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedOnePlace(from = 0, by = 1))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").movedOnePlace(from = 2, by = -1))
    }

    private fun setlistWithSongs(name: String, description: String, vararg songFileNames: String) = SetlistWithSongs(
        setlist = Setlist(
            fileName = "$name.setlist.json",
            title = name,
            description = description,
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = songFileNames.map { Setlist.Entry(songFileName = it) },
            size = 0,
        ),
        entries = songFileNames.mapIndexed { index, songFileName -> SetlistWithSongs.Entry.Missing(index = index, songFileName = songFileName) },
    )
}
