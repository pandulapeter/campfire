package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase

class SaveSongContentUseCaseImpl internal constructor(
    private val songRepository: SongRepository
) : SaveSongContentUseCase {

    override suspend operator fun invoke(content: SongContent) = songRepository.saveSong(content)
}
