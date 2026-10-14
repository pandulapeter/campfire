/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult

interface ImportFilesUseCase {

    /**
     * Carries out what [PrepareImportUseCase] worked out. A file the library already has under the same name and
     * with the same content is never written a second time. [choices] are what the user answered in the review: an
     * entry left out is not written, and a repeat of it goes with it; one whose name is taken by something else is
     * numbered next to it or written over it, as its resolution says. Progress counts every processed entry,
     * including duplicates and the ones left out. A write failure stops the batch and returns
     * its partial result with the failed and unprocessed names; cancellation still throws. The observer must not
     * throw or perform blocking work. A setlist that arrived naming no day of its own is written with the day it was
     * imported on, the way a new one is, unless it replaces a library setlist, whose day and countdown it keeps.
     */
    suspend operator fun invoke(
        plan: ImportPlan,
        choices: ImportChoices,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult
}
