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
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult

interface ImportFilesUseCase {

    /**
     * Carries out what [PrepareImportUseCase] worked out. A file the library already has under the same name and
     * with the same content is never written a second time; [resolution] decides what happens to the ones whose
     * name is taken by something else, and is the answer the user gave to that question.
     */
    suspend operator fun invoke(plan: ImportPlan, resolution: ImportConflictResolution): ImportResult
}
