/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase

/**
 * The writing half of [ApplyImportUseCase], internal to this module like the records it keeps after it: an import that
 * called this alone would leave the demo library's and the shared links' records unwritten.
 */
internal interface ImportFilesUseCase {

    /**
     * Writes [plan] as [choices] answer it, by the rules [ApplyImportUseCase] states for its writing, and nothing more;
     * a cancellation still throws, which is the caller's to hold off.
     */
    suspend operator fun invoke(
        plan: ImportPlan,
        choices: ImportChoices,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult
}
