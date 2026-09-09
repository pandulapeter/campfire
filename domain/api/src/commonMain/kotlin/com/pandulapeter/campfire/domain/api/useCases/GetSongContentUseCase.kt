package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SongContent

interface GetSongContentUseCase {

    /** Null if the file is missing or could not be read. */
    suspend operator fun invoke(fileName: String): SongContent?
}
