package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.SheetRepository
import com.pandulapeter.campfire.domain.resultOf

class GetCollectionsUseCase internal constructor(
    private val collectionRepository: CollectionRepository,
    private val sheetRepository: SheetRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        sheetRepository.getSheets().filter { it.isActive }.flatMap { sheet ->
            collectionRepository.getCollections(sheet.url, isForceRefresh)
        }.distinctBy { it.id }
    }
}