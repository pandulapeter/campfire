package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase

class CreateSongUseCaseImpl internal constructor(
    private val songRepository: SongRepository
) : CreateSongUseCase {

    /** The new file holds only what the user typed, so that the editor opens on a song that already has its title. */
    override suspend operator fun invoke(title: String, artist: String) = songRepository.createSong(
        title = title.trim(),
        artist = artist.trim(),
        text = buildString {
            append("{title: ").append(title.trim()).append("}\n")
            if (artist.isNotBlank()) append("{artist: ").append(artist.trim()).append("}\n")
            append("\n")
        }
    )
}
