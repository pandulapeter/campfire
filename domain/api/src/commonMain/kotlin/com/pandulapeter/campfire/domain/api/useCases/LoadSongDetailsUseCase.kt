package com.pandulapeter.campfire.domain.api.useCases

interface LoadSongDetailsUseCase {

    suspend operator fun invoke(url: String, isForceRefresh: Boolean)
}