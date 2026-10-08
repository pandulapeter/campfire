/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetlistEntryChangeTest {

    private val setlist = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(Setlist.Entry(songFileName = "a.cho"), Setlist.Entry(songFileName = "b.cho", capo = 2)),
        size = 0,
    )

    @Test
    fun `only the song's own entry is changed`() = assertEquals(
        listOf(Setlist.Entry(songFileName = "a.cho", tempo = 90), Setlist.Entry(songFileName = "b.cho", capo = 2)),
        setlist.withEntry("a.cho") { it.copy(tempo = 90) }?.entries,
    )

    @Test
    fun `a song the setlist does not hold is no change`() = assertNull(setlist.withEntry("c.cho") { it.copy(tempo = 90) })
}
