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
 * Whether the batch puts the import screen up while it is read: every one the user started, but the demo library, which
 * is written as it is, and but a single song file from anywhere and a text pasted as a new song, which are read in a
 * moment and turn out to be one song as a rule: the preview of that song (see [isPreviewed]) or the editor on it is what
 * comes up then, and the import screen only for what they turn out to be otherwise. Nothing is written before an Import
 * either way. With the songs switched off nothing is previewed and no editor is started, so the import screen is up from
 * the start, with its warning that what is imported will not be seen; the demo library stays unreviewed whatever the
 * switch.
 */
internal fun ImportRequest.isReportedWhileRead(areSongsEnabled: Boolean) = shouldReview && (
    !areSongsEnabled || !isNewSongFromText && (source as? ImportSource.Files)?.files?.singleOrNull()?.let { isSingleSong(it.name) } != true
)

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
