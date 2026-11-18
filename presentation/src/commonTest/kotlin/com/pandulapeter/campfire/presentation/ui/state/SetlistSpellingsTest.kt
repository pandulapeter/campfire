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

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SetlistSpellingsTest {

    private val songs = listOf(song("artist-song.cho"), song("café.cho"), song("b.cho"))

    @Test
    fun `an entry spelt in another case or Unicode form names the library's file and keeps its overrides`() = assertEquals(
        listOf(
            Setlist.Entry(songFileName = "artist-song.cho", transposition = 2),
            Setlist.Entry(songFileName = "café.cho"),
            Setlist.Entry(songFileName = "b.cho"),
        ),
        setlistsSpeltAsLibrary(
            setlists = listOf(setlist(Setlist.Entry("Artist-Song.cho", transposition = 2), Setlist.Entry("Café.cho"), Setlist.Entry("b.cho"))),
            songs = songs,
        ).single().entries,
    )

    @Test
    fun `a setlist whose every entry is spelt as the library's is the same object`() {
        val setlist = setlist(Setlist.Entry("b.cho"))
        assertSame(setlist, setlistsSpeltAsLibrary(listOf(setlist), songs).single())
    }

    @Test
    fun `a missing song keeps the setlist's name`() = assertEquals(
        listOf("gone.cho", "b.cho"),
        setlistsSpeltAsLibrary(listOf(setlist(Setlist.Entry("gone.cho"), Setlist.Entry("B.cho"))), songs).single().entries.map { it.songFileName },
    )

    @Test
    fun `two spellings of one song are listed once`() = assertEquals(
        listOf("b.cho"),
        setlistsSpeltAsLibrary(listOf(setlist(Setlist.Entry("b.cho"), Setlist.Entry("B.cho"))), songs).single().entries.map { it.songFileName },
    )

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    private fun setlist(vararg entries: Setlist.Entry) = Setlist(
        fileName = "gig.setlist.json",
        title = "Gig",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = entries.toList(),
        size = 0,
    )
}
