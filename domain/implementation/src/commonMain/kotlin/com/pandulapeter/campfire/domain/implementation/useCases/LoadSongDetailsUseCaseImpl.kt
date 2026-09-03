package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase

class LoadSongDetailsUseCaseImpl internal constructor(
    private val rawSongDetailsRepository: RawSongDetailsRepository
) : LoadSongDetailsUseCase {

    override suspend fun invoke(url: String, isForceRefresh: Boolean) = rawSongDetailsRepository.loadRawSongDetails(url, isForceRefresh)
}