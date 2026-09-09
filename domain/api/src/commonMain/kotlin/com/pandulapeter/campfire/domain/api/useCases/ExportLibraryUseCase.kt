package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile

interface ExportLibraryUseCase {

    /** A zip of every song and setlist, null when the library is empty. */
    suspend operator fun invoke(): ExportedFile?
}
