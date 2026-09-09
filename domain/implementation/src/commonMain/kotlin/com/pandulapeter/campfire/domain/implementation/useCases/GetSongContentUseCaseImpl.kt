package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase

class GetSongContentUseCaseImpl internal constructor(
    private val songContentRepository: SongContentRepository
) : GetSongContentUseCase {

    override suspend operator fun invoke(fileName: String) = songContentRepository.loadSongContent(fileName)
}
