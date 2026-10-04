/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult

interface ImportFilesUseCase {

    /**
     * Carries out what [PrepareImportUseCase] worked out. A file the library already has under the same name and
     * with the same content is never written a second time; [resolution] decides what happens to the ones whose
     * name is taken by something else, and is the answer the user gave to that question. Progress counts every
     * processed entry, including duplicates and skipped conflicts. A write failure stops the batch and returns
     * its partial result with the failed and unprocessed names; cancellation still throws. The observer must not
     * throw or perform blocking work.
     *
     * @param isDatingUndatedSetlists Whether a setlist that names no day of its own is dated by the day it is imported
     *   on, the way a new one is. False for the bundled demo setlist: it is planted on every installation, and two
     *   devices that planted it on different days would otherwise hold two different files under one name, which their
     *   first sync run keeps side by side as a conflict. A setlist that replaces a library one keeps that one's day
     *   either way.
     */
    suspend operator fun invoke(
        plan: ImportPlan,
        resolution: ImportConflictResolution,
        isDatingUndatedSetlists: Boolean = true,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult
}
