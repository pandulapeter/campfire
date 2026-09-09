package com.pandulapeter.campfire.domain.api.useCases

interface DeleteSongUseCase {

    /** Deletes the file and removes the song from every setlist and from the saved transpositions. */
    suspend operator fun invoke(fileName: String)
}
