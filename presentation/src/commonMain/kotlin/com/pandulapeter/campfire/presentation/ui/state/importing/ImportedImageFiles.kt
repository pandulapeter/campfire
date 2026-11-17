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
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * This plan with the pictures that arrived on their own ([ImportPlan.imageFiles]) reported the way a file the import does
 * not take is, by name among the skipped ones, and nothing else of them kept: what a batch whose pictures do not become
 * a new song ([pictureSong]) is reviewed with, rather than leaving them out silently.
 */
internal fun ImportPlan.withImageFilesSkipped() =
    if (imageFiles.isEmpty()) this else copy(skippedFileNames = skippedFileNames + imageFiles.map { it.name }, imageFiles = emptyList())

/**
 * The new song a batch of pictures alone comes to, nothing else in it (no song, setlist, or file left out): the editor
 * opens on it, the pictures kept as draft pictures, as a pasted chord sheet's is (see `EditorSession.openNewSongEditor`).
 * Several pictures are one song showing them all, in the order they arrived, titled by the first one's file name, the
 * way a photographed chart of several pages arrives. A picture left unread for its size comes along unbrought
 * ([ImportedImage.Problem.TOO_LARGE]), after the rest, so that the editor says why it is missing, as it does for one added
 * from the editor, rather than the batch going to the review for it: an oversized name of a picture's is only ever one
 * chosen on its own (`ImportBudget.readImage`). Null for any other batch.
 */
internal fun ImportPlan.pictureSong(): ImportPlan.SongEntry? {
    val first = (imageFiles.firstOrNull() ?: oversizedFileNames.firstOrNull()?.let { ImportedFile.unread(it) }) ?: return null
    if (songs.isNotEmpty() || setlists.isNotEmpty() || skippedFileNames.isNotEmpty() || unreadableDocumentFileNames.isNotEmpty()) return null
    if (!oversizedFileNames.all(LibraryFiles::isImageFileName)) return null
    return ImportPlan.SongEntry(
        fileName = first.name,
        text = "",
        status = ImportPlan.Status.NEW,
        sourceFileName = first.name,
        title = first.baseName,
        images = imageFiles.map { ImportPlan.SongImage(source = it.name, name = null, bytes = it.bytes, problem = null) } +
            oversizedFileNames.map { ImportPlan.SongImage(source = it, name = null, bytes = null, problem = ImportedImage.Problem.TOO_LARGE) },
    )
}

/**
 * Whether this is the song [ImportPlan.pictureSong] makes of pictures that arrived alone, which has no text of its own,
 * rather than a text that brings pictures (an archive's song, or one naming pictures by their addresses).
 */
internal val ImportPlan.SongEntry.isPictureSong get() = text.isEmpty() && images.isNotEmpty()
