package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile

interface ExportSetlistUseCase {

    /** A zip of the setlist and the songs in it, so that importing it elsewhere restores both. */
    suspend operator fun invoke(setlistFileName: String): ExportedFile?
}
