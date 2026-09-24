/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The screen data is built in two halves, so that a setlist being written does not filter and sort the whole library
 * again; these pin both that and the way the states of the inputs decide the state of the result.
 */
class GetScreenDataUseCaseImplTest {

    private val setlists = MutableStateFlow<DataState<List<Setlist>>>(DataState.Idle(listOf(setlist("b", priority = 1), setlist("a", priority = 2))))
    private val songs = MutableStateFlow<DataState<List<Song>>>(DataState.Idle(listOf(song("Yesterday"), song("Hey Jude"))))
    private val preferences = MutableStateFlow<DataState<UserPreferences>>(DataState.Idle(PREFERENCES))
    private val filter = MutableStateFlow(SongFilter())
    private val useCase = GetScreenDataUseCaseImpl(
        normalizeText = object : NormalizeTextUseCase {
            override fun invoke(text: String) = text.lowercase()
        },
        setlistRepository = FakeSetlistRepository(setlists),
        songRepository = FakeSongRepository(songs),
        userPreferencesRepository = FakeUserPreferencesRepository(preferences),
    )

    @Test
    fun `a setlist change does not rebuild the song list`() = runTest {
        val latest = collectScreenData()
        val first = latest.idle()

        setlists.value = DataState.Idle(setlists.value.data.orEmpty() + setlist("c", priority = 3))
        val second = latest.first { it?.data?.setlists?.size == 3 }?.data

        assertSame(first.songSections, second?.songSections)
        assertEquals(listOf("Hey Jude", "Yesterday"), second?.songs?.map { it.title })
    }

    @Test
    fun `the setlists follow their own sorting mode without the songs being rebuilt`() = runTest {
        val latest = collectScreenData()
        val first = latest.idle()
        assertEquals(listOf("a", "b"), first.setlists.map { it.title })

        // The setlist first and the preference second, each awaited: the two halves are collected side by side, so two
        // changes made at once may arrive in either order.
        setlists.value = DataState.Idle(setlists.value.data.orEmpty() + setlist("c", priority = 3))
        assertEquals(listOf("c", "a", "b"), latest.first { it?.data?.setlists?.size == 3 }?.data?.setlists?.map { it.title })
        preferences.value = DataState.Idle(PREFERENCES.copy(setlistSortingMode = UserPreferences.SetlistSortingMode.BY_TITLE))
        val second = latest.first { it?.data?.setlists?.firstOrNull()?.title == "a" }?.data

        assertEquals(listOf("a", "b", "c"), second?.setlists?.map { it.title })
        assertSame(first.songSections, second?.songSections)
    }

    @Test
    fun `a song preference rebuilds the song list`() = runTest {
        val latest = collectScreenData()
        val first = latest.idle()

        preferences.value = DataState.Idle(PREFERENCES.copy(sortingMode = UserPreferences.SortingMode.BY_ARTIST))
        val second = latest.first { it?.data != null && it.data !== first }?.data

        assertNotSame(first.songSections, second?.songSections)
    }

    @Test
    fun `a failure beats a load, which beats every input being idle`() = runTest {
        val latest = collectScreenData()
        latest.idle()

        songs.value = DataState.Loading(songs.value.data)
        latest.first { it is DataState.Loading }
        setlists.value = DataState.Failure(setlists.value.data)
        latest.first { it is DataState.Failure }
    }

    @Test
    fun `a load with no data carries the last good screen data`() = runTest {
        val latest = collectScreenData()
        val last = latest.idle()

        songs.value = DataState.Loading(null)
        val loading = latest.first { it is DataState.Loading }

        assertSame(last, loading?.data)
    }

    @Test
    fun `a filter value the other group counts down to nothing stays on the list with a zero, after the rest`() = runTest {
        songs.value = DataState.Idle(
            listOf(
                song("Yesterday", tags = listOf("Ballad"), languages = listOf("en")),
                song("Kalinka", tags = listOf("Folk"), languages = listOf("ru")),
                song("Katyusha", tags = listOf("Folk", "Ballad"), languages = listOf("ru")),
            )
        )
        filter.value = SongFilter(selectedLanguages = setOf("en"))
        val latest = collectScreenData()
        val screenData = latest.first { it?.data?.songs?.size == 1 }?.data

        assertEquals(listOf("Ballad" to 1, "Folk" to 0), screenData?.tags?.map { it.name to it.songCount })
        assertEquals(listOf("ru" to 2, "en" to 1), screenData?.languages?.map { it.code to it.songCount })

        filter.value = SongFilter(selectedTags = setOf("folk"))
        val narrowedByTag = latest.first { it?.data?.songs?.size == 2 }?.data

        assertEquals(listOf("ru" to 2, "en" to 0), narrowedByTag?.languages?.map { it.code to it.songCount })
    }

    @Test
    fun `several selected languages mean any of them or every one of them as the preferences say`() = runTest {
        songs.value = DataState.Idle(
            listOf(
                song("Yesterday", languages = listOf("en")),
                song("Kalinka", languages = listOf("ru")),
                song("Moscow Nights", languages = listOf("en", "ru")),
                song("Untitled"),
            )
        )
        filter.value = SongFilter(selectedLanguages = setOf("en", "ru"))
        val latest = collectScreenData()

        assertEquals(listOf("Kalinka", "Moscow Nights", "Yesterday"), latest.first { it?.data?.songs?.size == 3 }?.data?.songs?.map { it.title })

        preferences.value = DataState.Idle(PREFERENCES.copy(languageMatchMode = UserPreferences.MatchMode.ALL))
        assertEquals(listOf("Moscow Nights"), latest.first { it?.data?.songs?.size == 1 }?.data?.songs?.map { it.title })

        filter.value = SongFilter(selectedLanguages = setOf("en", SongLanguage.UNKNOWN))
        assertEquals(emptyList(), latest.first { it?.data?.songs?.isEmpty() == true }?.data?.songs)
    }

    @Test
    fun `an unreadable setlist folder does not hide the songs`() = runTest {
        setlists.value = DataState.Failure(null)

        val screenData = assertIs<DataState.Failure<ScreenData>>(collectScreenData().first { it != null }).data

        assertEquals(listOf("Hey Jude", "Yesterday"), screenData?.songs?.map { it.title })
        assertEquals(emptyList(), screenData?.setlists)
        assertEquals(false, screenData?.isWholeLibrary)
    }

    @Test
    fun `an unreadable song folder does not hide the setlists`() = runTest {
        songs.value = DataState.Failure(null)

        val screenData = assertIs<DataState.Failure<ScreenData>>(collectScreenData().first { it != null }).data

        assertEquals(listOf("a", "b"), screenData?.setlists?.map { it.title })
        assertEquals(emptyList(), screenData?.songs)
        assertEquals(false, screenData?.isWholeLibrary)
    }

    @Test
    fun `unreadable preferences sort by the defaults`() = runTest {
        songs.value = DataState.Idle(listOf(song("Hey Jude", artist = "Zed"), song("Yesterday", artist = "Abba")))
        preferences.value = DataState.Failure(null)

        val screenData = assertIs<DataState.Failure<ScreenData>>(collectScreenData().first { it != null }).data

        assertEquals(listOf("Yesterday", "Hey Jude"), screenData?.songs?.map { it.title })
        assertEquals(listOf("a", "b"), screenData?.setlists?.map { it.title })
        assertEquals(true, screenData?.isWholeLibrary)
    }

    @Test
    fun `a part that is still loading is not filled in`() = runTest {
        setlists.value = DataState.Loading(null)

        val state = assertIs<DataState.Loading<ScreenData>>(collectScreenData().first { it != null })

        assertEquals(null, state.data)
    }

    @Test
    fun `a failure after a complete read carries the complete library`() = runTest {
        val latest = collectScreenData()
        val last = latest.idle()

        setlists.value = DataState.Failure(null)
        val failure = latest.first { it is DataState.Failure }

        assertSame(last, failure?.data)
        assertTrue(last.isWholeLibrary)
    }

    @Test
    fun `a partial value is never cached`() = runTest {
        setlists.value = DataState.Failure(null)
        val latest = collectScreenData()
        assertFalse(assertIs<DataState.Failure<ScreenData>>(latest.first { it != null }).data?.isWholeLibrary ?: true)

        songs.value = DataState.Loading(null)
        latest.first { it is DataState.Failure && it.data == null }

        setlists.value = DataState.Idle(listOf(setlist("a", priority = 1)))
        songs.value = DataState.Idle(listOf(song("Yesterday")))
        val complete = assertIs<DataState.Idle<ScreenData>>(latest.first { it is DataState.Idle }).data
        assertTrue(complete.isWholeLibrary)

        setlists.value = DataState.Failure(null)
        assertSame(complete, latest.first { it is DataState.Failure }?.data)
    }

    /**
     * One collection for the whole test, since what is under test is what a collection reuses from one emission to
     * the next. The use case builds on [kotlinx.coroutines.Dispatchers.Default], so the emissions are awaited rather
     * than stepped through.
     */
    private fun TestScope.collectScreenData(): StateFlow<DataState<ScreenData>?> {
        val latest = MutableStateFlow<DataState<ScreenData>?>(null)
        backgroundScope.launch { useCase(filter).collect { latest.value = it } }
        return latest
    }

    private suspend fun StateFlow<DataState<ScreenData>?>.idle() = assertIs<DataState.Idle<ScreenData>>(first { it != null }).data

    private class FakeSetlistRepository(override val setlists: MutableStateFlow<DataState<List<Setlist>>>) : SetlistRepository {
        override suspend fun loadSetlistsIfNeeded() = throw UnsupportedOperationException()
        override suspend fun loadSetlistFileNamesNaming(songFileName: String) = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun createSetlist(title: String, description: String, priority: Int) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw UnsupportedOperationException()
        override suspend fun renameSetlist(fileName: String, title: String, description: String) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun loadSetlistDocument(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeSongRepository(override val songs: MutableStateFlow<DataState<List<Song>>>) : SongRepository {
        override suspend fun loadSongsIfNeeded() = throw UnsupportedOperationException()
        override suspend fun loadSongFileSizes() = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeUserPreferencesRepository(
        override val userPreferences: MutableStateFlow<DataState<UserPreferences>>,
    ) : UserPreferencesRepository {
        override suspend fun loadUserPreferencesIfNeeded() = throw UnsupportedOperationException()
        override suspend fun saveUserPreferences(userPreferences: UserPreferences) = throw UnsupportedOperationException()
        override suspend fun hasStoredUserPreferences() = throw UnsupportedOperationException()
    }

    private companion object {
        val PREFERENCES = UserPreferences(
            isPerformanceModeEnabled = false,
            shouldShowArchivedSetlists = false,
            isLyricsOnlyModeEnabled = false,
            isHorizontalSectionFlowEnabled = false,
            fontScale = 1f,
            sortingMode = UserPreferences.SortingMode.BY_TITLE,
            setlistSortingMode = UserPreferences.SetlistSortingMode.NEWEST_FIRST,
            uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
            themeColor = UserPreferences.ThemeColor.CAMPFIRE,
            language = UserPreferences.Language.SYSTEM_DEFAULT,
            chordSpelling = UserPreferences.ChordSpelling.Default,
            transpositions = emptyMap(),
            foldedSections = emptyMap(),
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
        )

        fun setlist(title: String, priority: Int) = Setlist(
            fileName = "$title.setlist.json",
            title = title,
            description = "",
            priority = priority,
            isArchived = false,
            entries = emptyList(),
            size = 0L,
        )

        fun song(
            title: String,
            tags: List<String> = emptyList(),
            languages: List<String> = emptyList(),
            artist: String = "The Beatles",
        ) = Song(
            fileName = "${title.lowercase().replace(' ', '_')}.cho",
            title = title,
            artist = artist,
            key = null,
            transpose = 0,
            tags = tags,
            languages = languages,
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0L,
            size = 0L,
        )
    }
}
