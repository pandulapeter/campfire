package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.GetSongDetailsUseCase

class GetSongDetailsUseCaseImpl internal constructor(
    private val rawSongDetailsRepository: RawSongDetailsRepository
) : GetSongDetailsUseCase {

    override operator fun invoke() = rawSongDetailsRepository.rawSongDetails
}