/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tick of the song picker moves one song in or out of the setlist as it is now, never the picker's own copy of it. */
internal class SetlistSongToggleTest {

    private val setlist = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(
            Setlist.Entry(songFileName = "a"),
            Setlist.Entry(songFileName = "b", transposition = 2),
            Setlist.Entry(songFileName = "c"),
        ),
        size = 0,
    )

    @Test
    fun `a song ticked in goes to the end and the others keep their overrides`() = assertEquals(
        setlist.entries + Setlist.Entry(songFileName = "d"),
        setlist.withSongTicked(songFileName = "d", isTicked = true).entries,
    )

    @Test
    fun `ticking a song the setlist names changes nothing`() =
        assertEquals(setlist, setlist.withSongTicked(songFileName = "b", isTicked = true))

    @Test
    fun `a song ticked out is removed and the rest keep their order`() = assertEquals(
        listOf("a", "c"),
        setlist.withSongTicked(songFileName = "b", isTicked = false).entries.map { it.songFileName },
    )

    @Test
    fun `a song another device added is kept through a tick`() {
        val synced = setlist.copy(entries = setlist.entries + Setlist.Entry(songFileName = "x"))
        assertEquals(
            listOf("a", "b", "c", "x", "d"),
            synced.withSongTicked(songFileName = "d", isTicked = true).entries.map { it.songFileName },
        )
        assertEquals(
            listOf("b", "c", "x"),
            synced.withSongTicked(songFileName = "a", isTicked = false).entries.map { it.songFileName },
        )
    }
}
