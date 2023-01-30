package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.GetSongDetailsUseCase

class GetSongDetailsUseCaseImpl internal constructor(
    private val songDetailsRepository: SongDetailsRepository
) : GetSongDetailsUseCase {

    override operator fun invoke() = songDetailsRepository.songDetails
}