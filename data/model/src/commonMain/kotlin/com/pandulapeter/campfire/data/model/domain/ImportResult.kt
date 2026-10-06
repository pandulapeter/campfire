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
    /**
     * Files the library already had under the same name and with the same content, each named once, however many copies
     * the batch brought. Counted separately from the skipped ones: nothing went wrong with them, there was simply nothing
     * left to do, see [ImportPlan.Status].
     */
    val duplicateFileNames: List<String> = emptyList(),
    /** Files the import would have looked inside but did not read, see [ImportPlan.oversizedFileNames]. */
    val oversizedFileNames: List<String> = emptyList(),
    val unreadableDocumentFileNames: List<String> = emptyList(),
    /** Conflicts explicitly left out by the user's batch decision, separate from unsupported files. */
    val skippedConflictingFileNames: List<String> = emptyList(),
    /** A stopped import retains the names it wrote, so a failure never hides a partial success. */
    val isFailed: Boolean = false,
    val failedFileNames: List<String> = emptyList(),
    val unprocessedFileNames: List<String> = emptyList(),
    /** Stored names of converted songs that were actually written by this import. */
    val convertedSongFileNames: List<String> = emptyList(),
    /** The only converted song of a selection, eligible for the result's explicit Open action. */
    val convertedSongToOpen: String? = null,
)
