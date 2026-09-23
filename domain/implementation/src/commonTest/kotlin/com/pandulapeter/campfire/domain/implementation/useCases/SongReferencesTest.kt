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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The walk a rename and a deletion make once the song's file has moved or gone: every reference is attempted whatever
 * fails, since the file cannot be put back, and the answer is whether all of them followed.
 */
class SongReferencesTest {

    private val setlists = FakeSetlistRepository(
        mutableMapOf(
            "first.setlist.json" to setlist("first.setlist.json", "a.cho", "b.cho"),
            "second.setlist.json" to setlist("second.setlist.json", "b.cho", "a.cho"),
            "other.setlist.json" to setlist("other.setlist.json", "b.cho"),
        ),
    )
    private val preferences = FakeUserPreferencesRepository(mapOf("a.cho" to 2, "b.cho" to 1))

    @Test
    fun `a rename moves every entry and the transposition`() = runTest {
        assertTrue(follow(newFileName = "c.cho"))

        assertEquals(listOf("c.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho", "c.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("other.setlist.json"))
        assertEquals(mapOf("b.cho" to 1, "c.cho" to 2), preferences.transpositions)
    }

    @Test
    fun `a deletion drops every entry and the transposition`() = runTest {
        assertTrue(follow(newFileName = null))

        assertEquals(listOf("b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `setlists that cannot be listed are a failure and the transposition still follows`() = runTest {
        setlists.isListingBroken = true

        assertFalse(follow(newFileName = "c.cho"))
        assertEquals(mapOf("b.cho" to 1, "c.cho" to 2), preferences.transpositions)

        assertFalse(follow(newFileName = null, fileName = "c.cho"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `a setlist that cannot be written does not stop the rest`() = runTest {
        setlists.unwritable = setOf("first.setlist.json")

        assertFalse(follow(newFileName = null))

        assertEquals(listOf("a.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
        assertEquals(mapOf("b.cho" to 1), preferences.transpositions)
    }

    @Test
    fun `a setlist that went away is nothing left to follow`() = runTest {
        setlists.gone = setOf("first.setlist.json")

        assertTrue(follow(newFileName = null))
        assertEquals(listOf("b.cho"), setlists.entriesOf("second.setlist.json"))
    }

    @Test
    fun `a deletion answers what the walk answered`() = runTest {
        setlists.unwritable = setOf("first.setlist.json")
        val songs = FakeSongRepository()

        assertFalse(DeleteSongUseCaseImpl(songs, setlists, preferences).invoke("a.cho"))
        assertEquals(listOf("a.cho"), songs.deleted)
    }

    @Test
    fun `a song that could not be deleted is not walked`() = runTest {
        val songs = FakeSongRepository(isBroken = true)

        assertFailsWith<IllegalStateException> { DeleteSongUseCaseImpl(songs, setlists, preferences).invoke("a.cho") }
        assertEquals(listOf("a.cho", "b.cho"), setlists.entriesOf("first.setlist.json"))
        assertEquals(mapOf("a.cho" to 2, "b.cho" to 1), preferences.transpositions)
    }

    private suspend fun follow(newFileName: String?, fileName: String = "a.cho") = followSongReferences(
        setlistRepository = setlists,
        userPreferencesRepository = preferences,
        fileName = fileName,
        newFileName = newFileName,
    )

    private class FakeSetlistRepository(private val files: MutableMap<String, Setlist>) : SetlistRepository {
        var isListingBroken = false
        var unwritable = emptySet<String>()

        /** Listed, but deleted by the time the walk asks to change them. */
        var gone = emptySet<String>()

        fun entriesOf(fileName: String) = files.getValue(fileName).entries.map { it.songFileName }

        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        override suspend fun loadSetlistsIfNeeded() = throw UnsupportedOperationException()
        override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
            if (isListingBroken) throw IllegalStateException("The setlists could not be listed.")
            return files.values.filter { setlist -> setlist.entries.any { it.songFileName == songFileName } }.map { it.fileName }
        }

        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun createSetlist(title: String, description: String, priority: Int) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? {
            if (fileName in unwritable) throw IllegalStateException("The setlist could not be written.")
            if (fileName in gone) return null
            return files[fileName]?.let(transform)?.also { files[fileName] = it }
        }

        override suspend fun renameSetlist(fileName: String, title: String, description: String) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun loadSetlistDocument(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeUserPreferencesRepository(var transpositions: Map<String, Int>) : UserPreferencesRepository {
        override val userPreferences: Flow<DataState<UserPreferences>> = emptyFlow()
        override suspend fun loadUserPreferencesIfNeeded() = PREFERENCES.copy(transpositions = transpositions)
        override suspend fun saveUserPreferences(userPreferences: UserPreferences) {
            transpositions = userPreferences.transpositions
        }

        override suspend fun hasStoredUserPreferences() = throw UnsupportedOperationException()
    }

    private class FakeSongRepository(private val isBroken: Boolean = false) : SongRepository {
        val deleted = mutableListOf<String>()
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = throw UnsupportedOperationException()
        override suspend fun loadSongFileSizes() = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) {
            if (isBroken) throw IllegalStateException("The file could not be deleted.")
            deleted += fileName
        }
    }

    private companion object {
        val PREFERENCES = UserPreferences(
            isPerformanceModeEnabled = false,
            shouldShowSongsWithoutChords = true,
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
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
        )

        fun setlist(fileName: String, vararg songs: String) = Setlist(
            fileName = fileName,
            title = fileName,
            description = "",
            priority = 0,
            isArchived = false,
            entries = songs.map { Setlist.Entry(songFileName = it) },
            size = 0L,
        )
    }
}
