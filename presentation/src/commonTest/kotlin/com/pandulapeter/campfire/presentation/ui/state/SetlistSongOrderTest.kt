/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SetlistSongOrderTest {

    private val a = Setlist.Entry(songFileName = "a.cho", transposition = 2)
    private val b = Setlist.Entry(songFileName = "b.cho", tempo = 90)
    private val c = Setlist.Entry(songFileName = "c.cho", capo = 3)

    @Test
    fun `a dragged order is written with every entry keeping its own transposition, tempo and capo`() {
        assertEquals(listOf(c, a, b), setlist(a, b, c).withSongOrder(names(c, a, b)).entries)
    }

    @Test
    fun `an entry a sync run appended during the drag stays last`() {
        val appended = Setlist.Entry(songFileName = "d.cho")
        assertEquals(listOf(c, a, b, appended), setlist(a, b, c, appended).withSongOrder(names(c, a, b)).entries)
    }

    @Test
    fun `an entry a sync run removed during the drag does not come back`() {
        assertEquals(listOf(c, a), setlist(a, c).withSongOrder(names(c, a, b)).entries)
    }

    @Test
    fun `a name the setlist does not hold is ignored`() {
        assertEquals(listOf(c, a, b), setlist(a, b, c).withSongOrder(listOf("c.cho", "unknown.cho", "a.cho", "b.cho")).entries)
    }

    private fun names(vararg entries: Setlist.Entry) = entries.map { it.songFileName }

    private fun setlist(vararg entries: Setlist.Entry) = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = entries.toList(),
        size = 0L,
    )
}
