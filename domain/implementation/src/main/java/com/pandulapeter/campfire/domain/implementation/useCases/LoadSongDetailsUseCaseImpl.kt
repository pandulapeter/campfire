package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase

class LoadSongDetailsUseCaseImpl internal constructor(
    private val songDetailsRepository: SongDetailsRepository
) : LoadSongDetailsUseCase {

    override suspend fun invoke(song: Song?, isForceRefresh: Boolean) = songDetailsRepository.loadSongDetails(song, isForceRefresh)
}