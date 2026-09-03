package com.pandulapeter.campfire.domain.api.useCases

interface TransposeRawSongDetailsUseCase {

    operator fun invoke(rawData: String, transposition: Int): String
}
