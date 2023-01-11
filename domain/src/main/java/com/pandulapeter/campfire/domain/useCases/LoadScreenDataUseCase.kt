package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LoadScreenDataUseCase internal constructor(
    private val collectionRepository: CollectionRepository,
    private val databaseRepository: DatabaseRepository,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = coroutineScope {
        listOf(
            async { playlistRepository.loadPlaylistsIfNeeded() },
            async {
                val userPreferences = userPreferencesRepository.loadUserPreferencesIfNeeded()
                val databases = databaseRepository.loadDatabasesIfNeeded()
                    .filter { it.isEnabled }
                    .filterNot { it.url in userPreferences.unselectedDatabaseUrls }
                    .sortedBy { it.priority }
                val collectionDownloadTasks = databases.map { database ->
                    async { collectionRepository.loadCollections(database.url, isForceRefresh) }
                }
                val songsDownloadTasks = databases.map { database ->
                    async { songRepository.loadSongs(database.url, isForceRefresh) }
                }
                (collectionDownloadTasks + songsDownloadTasks).awaitAll()
            }
        ).awaitAll()
    }
}