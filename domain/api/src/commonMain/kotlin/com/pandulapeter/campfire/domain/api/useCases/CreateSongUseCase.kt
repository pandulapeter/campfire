package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Song

interface CreateSongUseCase {

    /** Writes a new file holding just the title and artist directives, and returns the song it became. */
    suspend operator fun invoke(title: String, artist: String): Song
}
