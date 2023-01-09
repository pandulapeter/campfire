package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.SongRepository

class AreSongsAvailableUseCase internal constructor(
    private val songRepository: SongRepository
) {
    operator fun invoke() = songRepository.areSongsAvailable()
}