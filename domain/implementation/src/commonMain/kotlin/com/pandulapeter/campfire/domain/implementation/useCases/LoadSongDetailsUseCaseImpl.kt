package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase

class LoadSongDetailsUseCaseImpl internal constructor(
    private val rawSongDetailsRepository: RawSongDetailsRepository
) : LoadSongDetailsUseCase {

    // TODO(step 05): isForceRefresh does nothing while the text can only come from local storage.
    override suspend fun invoke(url: String, isForceRefresh: Boolean) = rawSongDetailsRepository.loadRawSongDetails(url)
}
