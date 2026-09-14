/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A setlist file is the one thing the user can hand-edit into a shape the screens cannot draw: the rows and the
 * pages are keyed by the song's file name, so a song named twice has to be read as named once.
 */
internal class SetlistMappersTest {

    @Test
    fun aSongNamedTwiceIsReadOnceWithItsFirstTransposition() {
        val document = SetlistDocument(
            title = "Summer",
            songs = listOf(
                SetlistSongDocument(file = "a.cho", transposition = 2),
                SetlistSongDocument(file = ""),
                SetlistSongDocument(file = "b.cho"),
                SetlistSongDocument(file = "a.cho", transposition = -1),
            ),
        )
        assertEquals(
            expected = listOf(Setlist.Entry(songFileName = "a.cho", transposition = 2), Setlist.Entry(songFileName = "b.cho")),
            actual = document.toModel("summer.setlist.json").entries,
        )
    }

    @Test
    fun aSongNamedTwiceIsWrittenOnce() {
        val setlist = Setlist(
            fileName = "summer.setlist.json",
            title = "Summer",
            description = "",
            priority = 0,
            isArchived = false,
            entries = listOf(
                Setlist.Entry(songFileName = "a.cho", transposition = 2),
                Setlist.Entry(songFileName = "a.cho"),
            ),
        )
        assertEquals(
            expected = listOf(SetlistSongDocument(file = "a.cho", transposition = 2)),
            actual = setlist.toDocument().songs,
        )
    }
}
