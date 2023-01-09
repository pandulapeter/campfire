package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.resultOf

class GetSongsUseCase internal constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        songRepository.getSongs(isForceRefresh)
    }
}