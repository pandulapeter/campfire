/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Whether the batch opens the import screen before anything is written: every one the user started, but the demo
 * library, and but a single song file the system handed over, which is opened rather than reviewed - its name taken
 * is the one thing that still puts the screen up for it. A song picked from the app's own Import files is reviewed,
 * since one row and an Import is what the user asked to see; a document or an archive is reviewed whoever picked it.
 */
internal val ImportRequest.isReviewed
    get() = shouldReview && !(shouldOpenSong && (source as? ImportSource.Files)?.files?.singleOrNull()?.let { isSingleSong(it.name) } == true)

private fun isSingleSong(name: String) = LibraryFiles.isSongFileName(name) && !LibraryFiles.isArchiveFileName(name)

/**
 * Whether an import went the one way a snackbar is enough for: everything written, nothing left out and nothing
 * stopped. Files that were already in the library count as written, since nothing of them is lost.
 */
internal val ImportResult.isClean
    get() = !isFailed && skippedFileNames.isEmpty() && leftOutFileNames.isEmpty() &&
        oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty()

/** How many files the plan has something to say about, which is what makes an import more than one file's. */
internal val ImportPlan.entryCount
    get() = songs.size + setlists.size + skippedFileNames.size + oversizedFileNames.size + unreadableDocumentFileNames.size
