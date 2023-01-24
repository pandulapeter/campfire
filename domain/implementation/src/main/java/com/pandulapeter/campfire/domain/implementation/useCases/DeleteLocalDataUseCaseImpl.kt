package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase

class DeleteLocalDataUseCaseImpl internal constructor(
    private val songRepository: SongRepository
) : DeleteLocalDataUseCase {

    override suspend operator fun invoke() = songRepository.deleteLocalSongs()
}