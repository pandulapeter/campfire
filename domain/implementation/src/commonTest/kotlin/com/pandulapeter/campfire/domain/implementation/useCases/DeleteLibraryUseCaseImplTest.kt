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
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The deletion's own sync run carries the answer the user typed, and a run that is already going would keep it from
 * starting, so the order of what the use case asks for is what is pinned here.
 */
class DeleteLibraryUseCaseImplTest {

    private val events = mutableListOf<String>()
    private val preferences = FakeUserPreferencesRepository(events)

    @Test
    fun `stops the run going before deleting and starts one with the deletions allowed after`() = runTest {
        useCase().invoke()

        assertEquals(
            listOf(
                "cancelSynchronization",
                "deleteAllSongs",
                "deleteAllSetlists",
                "updateUserPreferences",
                "synchronize(DELETE_REMOTELY)",
            ),
            events,
        )
    }

    @Test
    fun `starts the run and throws when a song could not be deleted`() = runTest {
        val failure = IllegalStateException("The songs could not be deleted.")

        val thrown = assertFailsWith<IllegalStateException> { useCase(songsFailure = failure).invoke() }

        // Compared by message, since the coroutine machinery may hand back a copy that carries the stack it crossed.
        assertEquals(failure.message, thrown.message)
        assertEquals(
            listOf(
                "cancelSynchronization",
                "deleteAllSetlists",
                "updateUserPreferences",
                "synchronize(DELETE_REMOTELY)",
            ),
            events,
        )
    }

    @Test
    fun `clears the transpositions and the folded sections`() = runTest {
        useCase().invoke()

        assertEquals(emptyMap(), preferences.saved?.transpositions)
        assertEquals(emptyMap(), preferences.saved?.foldedSections)
    }

    private fun useCase(songsFailure: Exception? = null) = DeleteLibraryUseCaseImpl(
        songRepository = FakeSongRepository(events, songsFailure),
        setlistRepository = FakeSetlistRepository(events),
        userPreferencesRepository = preferences,
        syncRepository = FakeSyncRepository(events),
    )

    private class FakeSongRepository(private val events: MutableList<String>, private val failure: Exception?) : SongRepository {
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = throw NotImplementedError()
        override suspend fun loadSongFileSizes() = throw NotImplementedError()
        override suspend fun rescan() = throw NotImplementedError()
        override suspend fun refresh(fileNames: Set<String>) = throw NotImplementedError()
        override suspend fun adoptImported(songs: Collection<Song>) = throw NotImplementedError()
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw NotImplementedError()
        override suspend fun createSong(title: String, artist: String, text: String) = throw NotImplementedError()
        override fun importFileName(fallbackTitle: String, text: String) = throw NotImplementedError()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw NotImplementedError()
        override suspend fun renameSong(song: Song) = throw NotImplementedError()
        override suspend fun deleteSong(fileName: String) = throw NotImplementedError()

        override suspend fun deleteAllSongs() {
            failure?.let { throw it }
            events += "deleteAllSongs"
        }
    }

    private class FakeSetlistRepository(private val events: MutableList<String>) : SetlistRepository {
        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        override suspend fun loadSetlistsIfNeeded() = throw NotImplementedError()
        override suspend fun loadSetlistFileNamesNaming(songFileName: String) = throw NotImplementedError()
        override suspend fun rescan() = throw NotImplementedError()
        override suspend fun refresh(fileNames: Set<String>) = throw NotImplementedError()
        override suspend fun adoptImported(setlists: Collection<Setlist>) = throw NotImplementedError()
        override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = throw NotImplementedError()
        override suspend fun saveSetlist(setlist: Setlist) = throw NotImplementedError()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw NotImplementedError()
        override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) =
            throw NotImplementedError()
        override suspend fun parseSetlist(document: String) = throw NotImplementedError()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw NotImplementedError()
        override suspend fun loadSetlistFileSizes(): Map<String, Long> = throw NotImplementedError()
        override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?) = throw NotImplementedError()
        override suspend fun deleteSetlist(fileName: String) = throw NotImplementedError()

        override suspend fun deleteAllSetlists() {
            events += "deleteAllSetlists"
        }
    }

    private class FakeUserPreferencesRepository(private val events: MutableList<String>) : UserPreferencesRepository {
        var saved: UserPreferences? = null
        override val userPreferences: Flow<DataState<UserPreferences>> = emptyFlow()
        override suspend fun loadUserPreferencesIfNeeded() = throw NotImplementedError()
        override suspend fun saveUserPreferences(userPreferences: UserPreferences) = throw NotImplementedError()
        override suspend fun hasStoredUserPreferences() = throw NotImplementedError()

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
            events += "updateUserPreferences"
            saved = transform(PREFERENCES)
        }
    }

    private class FakeSyncRepository(private val events: MutableList<String>) : SyncRepository {
        override val syncState: Flow<SyncState> = emptyFlow()
        override val availableProviders: List<SyncProviderId> = emptyList()
        override suspend fun restore() = throw NotImplementedError()
        override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) = throw NotImplementedError()
        override suspend fun cancelConnection() = throw NotImplementedError()
        override suspend fun disconnect() = throw NotImplementedError()
        override suspend fun forgetStoredConnection() = throw NotImplementedError()
        override fun scheduleSynchronization() = throw NotImplementedError()
        override fun startScheduledSynchronization(): SyncProgress? = throw NotImplementedError()

        override fun synchronize(deletionPolicy: SyncDeletionPolicy): Boolean {
            events += "synchronize($deletionPolicy)"
            return true
        }

        override fun cancelSynchronization() {
            events += "cancelSynchronization"
        }
    }

    private companion object {
        val PREFERENCES = UserPreferences(
            isPerformanceModeEnabled = false,
            shouldShowArchivedSetlists = false,
            isLyricsOnlyModeEnabled = false,
            fontScale = 1f,
            sortingMode = UserPreferences.SortingMode.BY_TITLE,
            setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
            uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
            themeColor = UserPreferences.ThemeColor.CAMPFIRE,
            isAppIconThemed = true,
            isCoverArtEnabled = true,
            language = UserPreferences.Language.SYSTEM_DEFAULT,
            chordSpelling = UserPreferences.ChordSpelling.Default,
            transpositions = mapOf("foo.cho" to 2),
            foldedSections = mapOf("foo.cho" to setOf("Chorus")),
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
            tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
            languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        )
    }
}
