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
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.screens.setlists.ListedSetlists
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertNull(songListPlaceholder(idle(songs = listOf(song)), groups(listOf(SongGroup(header = null, songs = listOf(song))), library = 1, filtered = 1), isImporting = false))
    }

    @Test
    fun `a library whose every song is filtered out is told apart from one the search found nothing in`() {
        assertEquals(Placeholder.ALL_SONGS_HIDDEN, songListPlaceholder(idle(songs = emptyList()), groups(library = 1, filtered = 0), isImporting = false))
        assertEquals(Placeholder.NO_MATCHING_SONGS, songListPlaceholder(idle(songs = listOf(song)), groups(library = 1, filtered = 1), isImporting = false))
    }

    @Test
    fun `an empty library is only called empty once it has been read and nothing is being imported into it`() {
        val empty = ScreenData(setlists = emptyList(), songs = emptyList(), songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = emptyList())
        assertEquals(Placeholder.NO_SONGS, songListPlaceholder(DataState.Idle(empty), groups(), isImporting = false))
        assertEquals(Placeholder.LOADING, songListPlaceholder(DataState.Idle(empty), groups(), isImporting = true))
        assertEquals(Placeholder.LOADING, songListPlaceholder(DataState.Loading(null), groups(), isImporting = false))
        assertEquals(Placeholder.ERROR, songListPlaceholder(DataState.Failure(null), groups(), isImporting = false))
    }

    @Test
    fun `setlists that are all archived are told apart from setlists the search found nothing in`() {
        val screenData = idle(songs = emptyList(), setlists = listOf(setlist.setlist))
        assertNull(setlistListPlaceholder(screenData, listed(listOf(setlist), library = 1, unsearched = 1), isImporting = false))
        assertEquals(Placeholder.ALL_SETLISTS_HIDDEN, setlistListPlaceholder(screenData, listed(library = 1, unsearched = 0), isImporting = false))
        assertEquals(Placeholder.NO_MATCHING_SETLISTS, setlistListPlaceholder(screenData, listed(library = 1, unsearched = 1), isImporting = false))
    }

    @Test
    fun `a library without setlists says so`() {
        assertEquals(Placeholder.NO_SETLISTS, setlistListPlaceholder(idle(songs = listOf(song)), listed(), isImporting = false))
    }

    @Test
    fun `a list not yet rebuilt for new library data keeps the placeholder it had`() {
        assertEquals(Placeholder.NO_SETLISTS, setlistListPlaceholder(idle(songs = emptyList(), setlists = listOf(setlist.setlist)), listed(), isImporting = false))
        assertEquals(Placeholder.NO_SONGS, songListPlaceholder(idle(songs = listOf(song)), groups(), isImporting = false))
        assertEquals(Placeholder.LOADING, setlistListPlaceholder(idle(songs = emptyList()), listed(isLibraryRead = false), isImporting = false))
        assertEquals(Placeholder.LOADING, songListPlaceholder(idle(songs = listOf(song)), groups(isLibraryRead = false), isImporting = false))
    }

    @Test
    fun `a list behind the library holds the placeholder`() {
        val withSetlist = idle(songs = emptyList(), setlists = listOf(setlist.setlist))
        assertTrue(isListBehind(withSetlist, listedCount = 0) { it.setlists.size })
        assertTrue(isListBehind(idle(songs = listOf(song)), listedCount = 0) { it.unfilteredSongs.size })
        assertFalse(isListBehind(withSetlist, listedCount = 1) { it.setlists.size })
        assertFalse(isListBehind(idle(songs = listOf(song)), listedCount = 1) { it.unfilteredSongs.size })
        assertFalse(isListBehind(DataState.Loading(null), listedCount = 1) { it.setlists.size })
        assertFalse(isListBehind(DataState.Failure(null), listedCount = 1) { it.unfilteredSongs.size })
    }

    private fun groups(groups: List<SongGroup> = emptyList(), library: Int = 0, filtered: Int = 0, isLibraryRead: Boolean = true) = SongGroups(
        filterKey = "",
        orderKey = "",
        groups = groups,
        isLibraryRead = isLibraryRead,
        librarySongCount = library,
        filteredSongCount = filtered,
    )

    private fun listed(setlists: List<SetlistWithSongs> = emptyList(), library: Int = 0, unsearched: Int = 0, isLibraryRead: Boolean = true) = ListedSetlists(
        setlists = setlists,
        isLibraryRead = isLibraryRead,
        filterKey = "",
        libraryCount = library,
        unsearchedCount = unsearched,
    )

    /** A library of [song] alone, of which [songs] are what the filters let through. */
    private fun idle(songs: List<Song>, setlists: List<Setlist> = emptyList()) = DataState.Idle(
        ScreenData(setlists = setlists, songs = songs, songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = listOf(song)),
    )
}
