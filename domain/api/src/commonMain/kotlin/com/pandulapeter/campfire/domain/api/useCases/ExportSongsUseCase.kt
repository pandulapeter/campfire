package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile

interface ExportSongsUseCase {

    /** A single song leaves as the `.cho` file it already is, several as a zip. Null when none of them can be read. */
    suspend operator fun invoke(fileNames: List<String>): ExportedFile?
}
