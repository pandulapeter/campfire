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

import com.pandulapeter.campfire.data.model.domain.ExportedFile

interface ExportLibraryUseCase {

    /**
     * A zip of every song and setlist. Null when the library is empty **or could not be read**: an archive missing a
     * library it was supposed to contain is worse than no archive, since the user files it away as a backup.
     */
    suspend operator fun invoke(): Result?

    /**
     * [file] plus the names of the files the export could not read, which are left out of it: a song the library scan
     * skipped, one whose text could not be read, a setlist whose document could not be. On the web an export is the
     * only copy of the library there is, so a name missing from the archive is told to the user rather than logged.
     */
    data class Result(
        val file: ExportedFile,
        val skippedFileNames: List<String>,
    )
}
