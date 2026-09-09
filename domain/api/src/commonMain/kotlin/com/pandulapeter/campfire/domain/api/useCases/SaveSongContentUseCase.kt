package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SongContent

interface SaveSongContentUseCase {

    suspend operator fun invoke(content: SongContent)
}
