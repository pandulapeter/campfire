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
 * way a photographed chart of several pages arrives. Null for any other batch.
 */
internal fun ImportPlan.pictureSong(): ImportPlan.SongEntry? {
    val first = imageFiles.firstOrNull() ?: return null
    if (songs.isNotEmpty() || setlists.isNotEmpty() || skippedFileNames.isNotEmpty() || oversizedFileNames.isNotEmpty() || unreadableDocumentFileNames.isNotEmpty()) return null
    return ImportPlan.SongEntry(
        fileName = first.name,
        text = "",
        status = ImportPlan.Status.NEW,
        sourceFileName = first.name,
        title = first.baseName,
        images = imageFiles.map { ImportPlan.SongImage(source = it.name, name = null, bytes = it.bytes, problem = null) },
    )
}
