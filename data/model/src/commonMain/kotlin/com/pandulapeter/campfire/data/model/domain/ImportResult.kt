/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * What one import batch did, as the file names it produced. Skipped files are reported rather than hidden: a picker
 * lets the user choose anything, and silently ignoring half of it would look like the import lost them.
 */
data class ImportResult(
    val importedSongFileNames: List<String> = emptyList(),
    val importedSetlistFileNames: List<String> = emptyList(),
    val skippedFileNames: List<String> = emptyList(),
)
