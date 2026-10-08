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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListPlaceholdersTest {

    private val song = Song(
        fileName = "song.cho", title = "Song", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = false, canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
    private val setlist = SetlistWithSongs(
        setlist = Setlist(fileName = "set.setlist.json", title = "Set", description = "", date = LocalDate(2026, 1, 1), isArchived = true, entries = emptyList(), size = 0L),
        entries = emptyList(),
    )

    @Test
    fun `a song list with songs to show has no placeholder`() {
        assertNull(songListPlaceholder(idle(songs = listOf(song)), SongGroups("", listOf(SongGroup(header = null, songs = listOf(song)))), isImporting = false))
    }

    @Test
    fun `a library whose every song is filtered out is told apart from one the search found nothing in`() {
        assertEquals(Placeholder.ALL_SONGS_HIDDEN, songListPlaceholder(idle(songs = emptyList()), noGroups, isImporting = false))
        assertEquals(Placeholder.NO_MATCHING_SONGS, songListPlaceholder(idle(songs = listOf(song)), noGroups, isImporting = false))
    }

    @Test
    fun `an empty library is only called empty once it has been read and nothing is being imported into it`() {
        val empty = ScreenData(setlists = emptyList(), songs = emptyList(), songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = emptyList())
        assertEquals(Placeholder.NO_SONGS, songListPlaceholder(DataState.Idle(empty), noGroups, isImporting = false))
        assertEquals(Placeholder.LOADING, songListPlaceholder(DataState.Idle(empty), noGroups, isImporting = true))
        assertEquals(Placeholder.LOADING, songListPlaceholder(DataState.Loading(null), noGroups, isImporting = false))
        assertEquals(Placeholder.ERROR, songListPlaceholder(DataState.Failure(null), noGroups, isImporting = false))
    }

    @Test
    fun `setlists that are all archived are told apart from setlists the search found nothing in`() {
        val screenData = idle(songs = emptyList(), setlists = listOf(setlist.setlist))
        assertNull(setlistListPlaceholder(screenData, listOf(setlist), listOf(setlist), isImporting = false))
        assertEquals(Placeholder.ALL_SETLISTS_HIDDEN, setlistListPlaceholder(screenData, emptyList(), emptyList(), isImporting = false))
        assertEquals(Placeholder.NO_MATCHING_SETLISTS, setlistListPlaceholder(screenData, emptyList(), listOf(setlist), isImporting = false))
    }

    @Test
    fun `a library without setlists says so`() {
        assertEquals(Placeholder.NO_SETLISTS, setlistListPlaceholder(idle(songs = listOf(song)), emptyList(), emptyList(), isImporting = false))
    }

    private val noGroups = SongGroups(filterKey = "", groups = emptyList())

    /** A library of [song] alone, of which [songs] are what the filters let through. */
    private fun idle(songs: List<Song>, setlists: List<Setlist> = emptyList()) = DataState.Idle(
        ScreenData(setlists = setlists, songs = songs, songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = listOf(song)),
    )
}
