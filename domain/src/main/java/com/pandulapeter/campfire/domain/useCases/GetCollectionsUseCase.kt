package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.domain.resultOf

class GetCollectionsUseCase internal constructor(
    private val collectionRepository: CollectionRepository,
    private val databaseRepository: DatabaseRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        databaseRepository.getDatabases().filter { it.isActive }.flatMap { database ->
            collectionRepository.getCollections(database.url, isForceRefresh)
        }.distinctBy { it.id }
    }
}