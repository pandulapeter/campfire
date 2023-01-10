package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.resultOf

class GetSongsUseCase internal constructor(
    private val songRepository: SongRepository,
    private val databaseRepository: DatabaseRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        databaseRepository.getDatabases().filter { it.isActive }.flatMap { database ->
            songRepository.getSongs(database.url, isForceRefresh)
        }.distinctBy { it.id }
    }
}