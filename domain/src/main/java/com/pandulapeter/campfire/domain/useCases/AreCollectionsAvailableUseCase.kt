package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository

class AreCollectionsAvailableUseCase internal constructor(
    private val collectionRepository: CollectionRepository
) {
    operator fun invoke() = collectionRepository.areCollectionsAvailable()
}