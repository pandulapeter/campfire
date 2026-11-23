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
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Whether the batch puts the import screen up while it is read: every one the user started, but the demo library, which
 * is written as it is, and but a single song file from anywhere, a text pasted as a new song, and pictures alone (by
 * their names), which are read in a moment and turn out to be one song as a rule: the preview of that song (see
 * [isPreviewed]) or the editor on it (see [pictureSong]) is what comes up then, and the import screen only for what they turn out to be otherwise. Nothing is written before an Import
 * either way. With the songs switched off nothing is previewed and no editor is started, so the import screen is up from
 * the start, with its warning that what is imported will not be seen; the demo library stays unreviewed whatever the
 * switch. A page read by its address is the exception among the texts: reading it takes seconds, which the import
 * screen's loading stage says ("Reading the page…", with Cancel), and the editor its song comes to takes its place.
 */
internal fun ImportRequest.isReportedWhileRead(areSongsEnabled: Boolean) = shouldReview && (
    !areSongsEnabled || source is ImportSource.Page ||
        !isNewSongFromText && (source as? ImportSource.Files)?.files?.let { it.isSingleSong() || it.arePicturesAlone() } != true
)

private fun List<ImportedFile>.isSingleSong() = singleOrNull()?.let { LibraryFiles.isSongFileName(it.name) && !LibraryFiles.isArchiveFileName(it.name) } == true

private fun List<ImportedFile>.arePicturesAlone() = isNotEmpty() && all { LibraryFiles.isImageFileName(it.name) }

/**
 * Whether an import went the one way a snackbar is enough for: everything written, nothing left out (a picture or an
 * attachment a song names included) and nothing stopped. Files that were already in the library count as written, since
 * nothing of them is lost.
 */
internal val ImportResult.isClean
    get() = !isFailed && skippedFileNames.isEmpty() && leftOutFileNames.isEmpty() &&
        oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty() && songsWithFilesLeftOut.isEmpty()

/** How many files the plan has something to say about, which is what makes an import more than one file's. */
internal val ImportPlan.entryCount
    get() = songs.size + setlists.size + skippedFileNames.size + oversizedFileNames.size + unreadableDocumentFileNames.size
