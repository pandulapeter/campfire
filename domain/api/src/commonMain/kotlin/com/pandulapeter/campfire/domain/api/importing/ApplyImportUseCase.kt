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
     * Writes [plan] as [choices] answer it, then what [bookkeeping] says to record of what it wrote.
     *
     * The writing carries out what [PrepareImportUseCase] worked out. A file the library already has under the same name
     * and with the same content is never written a second time. An entry left out is not written, and a repeat of it goes
     * with it; one whose name is taken by something else is numbered next to it or written over it, as its resolution
     * says. Every song written carries the choices' tags as well as its own, a tag it already has in another case
     * included only once. Progress counts every processed entry, including duplicates and the ones left out. A write
     * failure stops the batch and returns its partial result with the failed and unprocessed names. The observer must
     * not throw or perform blocking work. A setlist that arrived naming no day of its own is written with the day it was
     * imported on, the way a new one is, unless it replaces a library setlist, whose day and countdown it keeps.
     *
     * The records are the demo library's files under their own names (a numbered copy of one is not the demo's), and for
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
