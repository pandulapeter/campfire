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
                val databaseUrls = databaseRepository.loadDatabasesIfNeeded()
                    .filter { it.isEnabled }
                    .filterNot { it.url in userPreferences.unselectedDatabaseUrls }
                    .sortedBy { it.priority }
                    .map { it.url }
                listOf(
                    async { collectionRepository.loadCollections(databaseUrls, isForceRefresh) },
                    async { songRepository.loadSongs(databaseUrls, isForceRefresh) }
                ).awaitAll()
            }
        ).awaitAll()
    }
}