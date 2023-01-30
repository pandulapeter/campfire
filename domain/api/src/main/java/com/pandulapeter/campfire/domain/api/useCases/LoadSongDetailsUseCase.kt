package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Song

interface LoadSongDetailsUseCase {

    suspend operator fun invoke(song: Song?, isForceRefresh: Boolean)
}