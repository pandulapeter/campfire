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

import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult

interface ApplyImportUseCase {

    /**
     * Writes [plan] as [choices] answer it, as [ImportFilesUseCase] does, and then what [bookkeeping] says to record of
     * what it wrote: the demo library's files under their own names (a numbered copy of one is not the demo's), and for
     * each shared link the names it asked for and wrote, so that the link's next review starts them on Replace
     * (`sharedLinkChoices`). Once started, the import and its records are written whole even if the caller is cancelled;
     * an exception from storage, a `CancellationException` included, still propagates.
     */
    suspend operator fun invoke(
        plan: ImportPlan,
        choices: ImportChoices,
        bookkeeping: ImportBookkeeping,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult
}
