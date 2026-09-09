package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile

interface ImportFilesUseCase {

    /** Never overwrites: a file whose name is taken lands next to the one it collides with. */
    suspend operator fun invoke(files: List<ImportedFile>): ImportResult
}
