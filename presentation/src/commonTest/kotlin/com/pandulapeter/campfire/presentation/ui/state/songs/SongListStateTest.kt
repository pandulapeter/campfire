/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.presentation.ui.LowercaseSearchText
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongLanguage
import com.pandulapeter.campfire.data.model.song.Tag
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.domain.api.library.SongFilter
import com.pandulapeter.campfire.domain.api.library.SongSection
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroupHeader
import com.pandulapeter.campfire.presentation.ui.search.SearchableSong
import com.pandulapeter.campfire.presentation.ui.search.SongSearchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongListStateTest {

    private val songFilter = MutableStateFlow(SongFilter())
    private val tags = MutableStateFlow(listOf(Tag(name = "Rock", songCount = 1)))
    private val languages = MutableStateFlow(listOf(SongLanguage(code = "en", songCount = 1)))

    @Test
    fun `a tag is toggled off whatever case it was selected in`() = runTest {
        val state = songListState()
        songFilter.value = SongFilter(selectedTags = setOf("Rock"))
        state.toggleTagFilter("rock")
        assertEquals(emptySet(), songFilter.value.selectedTags)
        state.toggleTagFilter("Pop")
        assertEquals(setOf("Pop"), songFilter.value.selectedTags)
    }

    @Test
    fun `clearing the tags keeps a selected tag the library no longer has`() = runTest {
        val state = songListState()
        songFilter.value = SongFilter(selectedTags = setOf("ROCK", "Gone"))
        state.clearTagFilter()
        assertEquals(setOf("Gone"), songFilter.value.selectedTags)
    }

    @Test
    fun `the filter is active only for what the filter controls show as selected`() = runTest {
        val state = songListState()
        songFilter.value = SongFilter(selectedTags = setOf("Gone"))
        runCurrent()
        assertFalse(state.isSongFilterActive.value, "A tag the library no longer has")

        songFilter.value = SongFilter(selectedLanguages = setOf("en"))
        runCurrent()
        assertFalse(state.isSongFilterActive.value, "A language when the library has only that one")

        languages.value = listOf(SongLanguage(code = "en", songCount = 1), SongLanguage(code = "hu", songCount = 1))
        runCurrent()
        assertTrue(state.isSongFilterActive.value, "A language out of two")

        songFilter.value = SongFilter(selectedTags = setOf("rock"))
        runCurrent()
        assertTrue(state.isSongFilterActive.value, "A tag of the library, in another case")
    }

    @Test
    fun `an empty normalized query keeps the sections`() {
        val first = searchable("first", "first", "a", emptyList())
        val second = searchable("second", "second", "b", emptyList())
        val sections = listOf(
            SongSection(header = SongSection.Header.Letter('F'), songs = listOf(first.song)),
            SongSection(header = SongSection.Header.Letter('S'), songs = listOf(second.song)),
        )
        assertEquals(
            listOf(
                SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('F')), songs = listOf(first.song)),
                SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('S')), songs = listOf(second.song)),
            ),
            songGroupsFor(sections, listOf(first, second), "", randomPicksSeed = 1L, isRandomPicksQuestionAsked = true),
        )
        assertEquals(
            listOf(SongGroup(header = null, songs = listOf(second.song))),
            songGroupsFor(sections, listOf(first, second), "sec", randomPicksSeed = null, isRandomPicksQuestionAsked = false),
        )
        assertEquals(emptyList(), songGroupsFor(sections, listOf(first, second), "nothing", randomPicksSeed = null, isRandomPicksQuestionAsked = false))
    }

    @Test
    fun `a long list is led by its random picks and their question, a search by neither`() {
        val songs = (1..21).map { song("song$it") }
        val sections = listOf(SongSection(header = SongSection.Header.Letter('S'), songs = songs))
        val searchable = songs.map { SearchableSong(song = it, title = it.title, artist = "", tags = emptyList()) }

        val groups = songGroupsFor(sections, searchable, "", randomPicksSeed = 7L, isRandomPicksQuestionAsked = true)
        assertEquals(
            SongGroup(header = SongGroupHeader.RandomPicks, songs = randomPicks(songs, seed = 7L), isRandomPicksQuestionAfter = true),
            groups.first(),
        )
        assertEquals(songs, groups.last().songs)

        assertEquals(1, songGroupsFor(sections, searchable, "", randomPicksSeed = null, isRandomPicksQuestionAsked = true).size, "Switched off")
        assertTrue(songGroupsFor(sections, searchable, "song", randomPicksSeed = 7L, isRandomPicksQuestionAsked = true).none { it.isRandomPicks })
    }

    private fun searchable(name: String, title: String, artist: String, tags: List<String>) = SearchableSong(
        song = song(name), title = title, artist = artist, tags = tags,
    )

    private fun song(name: String) = Song(
        fileName = "$name.cho", title = name, artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )

    private fun TestScope.songListState() = SongListState(
        scope = backgroundScope,
        savedStateStore = SavedStateStore(SavedStateHandle(), backgroundScope),
        mutableSongFilter = songFilter,
        screenData = MutableStateFlow<DataState<ScreenData>>(DataState.Loading(null)),
        indexedSongs = MutableStateFlow(LibraryState.IndexedSongs(sections = emptyList(), search = SongSearchSnapshot.Empty, filterKey = "", orderKey = "", sorted = emptyList(), isLibraryRead = true)),
        tags = tags,
        languages = languages,
        isImporting = MutableStateFlow(false),
        normalizeSearchText = LowercaseSearchText,
        changeUserPreferences = {},
        userPreferences = MutableStateFlow(null),
        randomPicksSeed = MutableStateFlow(0L),
    )
}
