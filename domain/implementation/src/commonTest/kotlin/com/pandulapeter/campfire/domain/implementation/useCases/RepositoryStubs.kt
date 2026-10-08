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
import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * What every member of a stub does until a test overrides it. An [Error] rather than an exception, because the use
 * cases catch exceptions where a repository may fail, and a call no test expected must not pass for one of those.
 */
private fun unexpected(): Nothing = throw NotImplementedError("Not expected to be called by this test.")

/** A [SongRepository] whose every member fails the same way, for a test to override only what it expects to be called. */
internal open class SongRepositoryStub : SongRepository {
    override val songs: Flow<DataState<List<Song>>> get() = unexpected()
    override suspend fun loadSongsIfNeeded(): List<Song>? = unexpected()
    override suspend fun loadSongFileSizes(): Map<String, Long> = unexpected()
    override suspend fun rescan(): Unit = unexpected()
    override suspend fun refresh(fileNames: Set<String>): Unit = unexpected()
    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean = unexpected()
    override suspend fun createSong(title: String, artist: String, text: String): Song = unexpected()
    override fun importFileName(fallbackTitle: String, text: String): String = unexpected()
    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song = unexpected()
    override suspend fun adoptImported(songs: Collection<Song>): Unit = unexpected()
    override suspend fun renameSong(song: Song): Song? = unexpected()
    override suspend fun deleteSong(fileName: String): Unit = unexpected()
    override suspend fun deleteAllSongs(): Unit = unexpected()
}

/** A [SetlistRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SetlistRepositoryStub : SetlistRepository {
    override val setlists: Flow<DataState<List<Setlist>>> get() = unexpected()
    override suspend fun loadSetlistsIfNeeded(): List<Setlist>? = unexpected()
    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> = unexpected()
    override suspend fun rescan(): Unit = unexpected()
    override suspend fun refresh(fileNames: Set<String>): Unit = unexpected()
    override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist = unexpected()
    override suspend fun saveSetlist(setlist: Setlist): Unit = unexpected()
    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? = unexpected()
    override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist? =
        unexpected()
    override suspend fun parseSetlist(document: String): ParsedSetlist? = unexpected()
    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist = unexpected()
    override suspend fun adoptImported(setlists: Collection<Setlist>): Unit = unexpected()
    override suspend fun loadSetlistFileSizes(): Map<String, Long> = unexpected()
    override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?): String? = unexpected()
    override suspend fun deleteSetlist(fileName: String): Unit = unexpected()
    override suspend fun deleteAllSetlists(): Unit = unexpected()
}

/** A [SongContentRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SongContentRepositoryStub : SongContentRepository {
    override val invalidations: Flow<Long> get() = unexpected()
    override suspend fun loadSongContent(fileName: String, useCache: Boolean): SongContent? = unexpected()
    override suspend fun invalidate(fileName: String?): Unit = unexpected()
    override suspend fun invalidate(fileNames: Set<String>): Unit = unexpected()
}

/** A [UserPreferencesRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class UserPreferencesRepositoryStub : UserPreferencesRepository {
    override val userPreferences: Flow<DataState<UserPreferences>> get() = unexpected()
    override suspend fun loadUserPreferencesIfNeeded(): UserPreferences? = unexpected()
    override suspend fun saveUserPreferences(userPreferences: UserPreferences): Unit = unexpected()
    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): Unit = unexpected()
    override suspend fun hasStoredUserPreferences(): Boolean = unexpected()
}

/** A [SyncRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SyncRepositoryStub : SyncRepository {
    override val syncState: Flow<SyncState> get() = unexpected()
    override val availableProviders: List<SyncProviderId> get() = unexpected()
    override suspend fun restore(): SyncRepository.RestoreResult = unexpected()
    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean = unexpected()
    override suspend fun cancelConnection(): Unit = unexpected()
    override suspend fun disconnect(): Unit = unexpected()
    override suspend fun forgetStoredConnection(): Unit = unexpected()
    override fun synchronize(deletionPolicy: SyncDeletionPolicy): Boolean = unexpected()
    override fun scheduleSynchronization(): Unit = unexpected()
    override fun startScheduledSynchronization(): SyncProgress? = unexpected()
    override fun cancelSynchronization(): Unit = unexpected()
}

/**
 * Keeps the files of the last archive it packed, and unpacks every archive into what [unpack] answers - nothing,
 * unless a test says otherwise.
 */
internal class FakeArchiveRepository(
    private val unpack: suspend (archive: ByteArray, maxSize: Long) -> List<ImportedFile> = { _, _ -> emptyList() },
) : ArchiveRepository {

    var packed: Map<String, ByteArray> = emptyMap()

    override suspend fun unpack(archive: ByteArray, maxSize: Long) = unpack.invoke(archive, maxSize)

    override suspend fun pack(files: Map<String, ByteArray>): ByteArray {
        packed = files
        return ByteArray(0)
    }
}
