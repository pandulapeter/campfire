package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.domain.resultOf

class GetCollectionsUseCase internal constructor(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        collectionRepository.getCollections(isForceRefresh)
    }
}