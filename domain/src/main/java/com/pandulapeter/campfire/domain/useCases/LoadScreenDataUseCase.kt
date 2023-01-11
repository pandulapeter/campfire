package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LoadScreenDataUseCase internal constructor(
    private val collectionRepository: CollectionRepository,
    private val databaseRepository: DatabaseRepository,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = coroutineScope {
        listOf(
            async {
                playlistRepository.loadPlaylistsIfNeeded()
            },
            async {
                databaseRepository.loadDatabasesIfNeeded().let { databases ->
                    databases.map { database ->
                        async { collectionRepository.loadCollections(database.url, isForceRefresh) }
                    } + databases.map { database ->
                        async { songRepository.loadSongs(database.url, isForceRefresh) }
                    }
                }.awaitAll()
            }
        ).awaitAll()
    }
}