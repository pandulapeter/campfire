package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.SheetRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.resultOf

class GetSongsUseCase internal constructor(
    private val songRepository: SongRepository,
    private val sheetRepository: SheetRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        sheetRepository.getSheets().filter { it.isActive }.flatMap { sheet ->
            songRepository.getSongs(sheet.url, isForceRefresh)
        }.distinctBy { it.id }
    }
}