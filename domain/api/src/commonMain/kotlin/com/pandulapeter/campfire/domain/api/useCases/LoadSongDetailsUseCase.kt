package com.pandulapeter.campfire.domain.api.useCases

interface LoadSongDetailsUseCase {

    /** @return Whether the song has text to show afterwards - false only when there was no saved copy to fall back on. */
    suspend operator fun invoke(url: String, isForceRefresh: Boolean): Boolean
}
