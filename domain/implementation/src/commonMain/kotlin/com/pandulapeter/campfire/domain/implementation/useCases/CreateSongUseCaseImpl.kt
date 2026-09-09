package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase

class CreateSongUseCaseImpl internal constructor(
    private val songRepository: SongRepository
) : CreateSongUseCase {

    /**
     * The new file holds what the user typed and the skeleton of a first verse, so that the editor opens on
     * something that is already shaped like a song rather than on an empty page.
     */
    override suspend operator fun invoke(title: String, artist: String) = songRepository.createSong(
        title = title.trim(),
        artist = artist.trim(),
        text = buildString {
            append("{title: ").append(title.trim()).append("}\n")
            if (artist.isNotBlank()) append("{artist: ").append(artist.trim()).append("}\n")
            append("{key: }\n")
            append("\n")
            append("{start_of_verse: Verse 1}\n")
            // The blank line the editor puts the caret on.
            append("\n")
            append("{end_of_verse}\n")
        }
    )
}
