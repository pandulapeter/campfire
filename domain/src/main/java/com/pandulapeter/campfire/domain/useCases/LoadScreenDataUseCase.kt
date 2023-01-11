package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository

class LoadScreenDataUseCase internal constructor(
    private val collectionRepository: CollectionRepository,
    private val databaseRepository: DatabaseRepository,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) {
        // TODO: Run these calls in parallel instead of sequentially
        val databases = databaseRepository.loadDatabasesIfNeeded()
        playlistRepository.loadPlaylistsIfNeeded()
        databases.forEach { database ->
            collectionRepository.loadCollections(database.url, isForceRefresh)
            songRepository.loadSongs(database.url, isForceRefresh)
        }
    }
}