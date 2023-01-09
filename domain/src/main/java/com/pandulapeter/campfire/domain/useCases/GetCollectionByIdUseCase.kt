package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.domain.resultOf

class GetCollectionByIdUseCase internal constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean,
        collectionId: String
    ) = resultOf {
        collectionRepository.getCollectionById(
            isForceRefresh = isForceRefresh,
            collectionId = collectionId
        )
    }
}