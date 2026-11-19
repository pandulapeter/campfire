/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import com.pandulapeter.campfire.data.model.importing.ImportLimits

/**
 * The collections Campfire's library is made of: songs and setlists, and [FILES], what songs keep besides their text.
 * Their ids are the folder names in the cloud and the keys of `sync-index.json`, which every release since sync
 * began reads and writes.
 *
 * [FILES] are the pictures songs show and the files attached to them, named by their content (see
 * [LibraryFiles.isLibraryFileName]) and kept while a song names them. The folder is named for files rather than images
 * so that both share its storage, sync, and pruning. A version without it leaves the folder alone on both sides, which
 * is what lets it share a cloud folder with this one.
 */
object LibraryCollections {

    val SONGS = SyncCollection(
        id = "songs",
        matches = LibraryFiles::isSongFileName,
        maxSize = ImportLimits.MAX_TEXT_FILE_SIZE,
        role = SyncCollection.Role.DOCUMENT,
    )

    val SETLISTS = SyncCollection(
        id = "setlists",
        matches = LibraryFiles::isSetlistFileName,
        maxSize = ImportLimits.MAX_TEXT_FILE_SIZE,
        role = SyncCollection.Role.DOCUMENT,
    )

    val FILES = SyncCollection(
        id = "files",
        matches = LibraryFiles::isLibraryFileName,
        maxSize = ImportLimits.MAX_ATTACHMENT_FILE_SIZE,
        role = SyncCollection.Role.CONTENT_FILE,
    )

    val all = SyncCollections(all = listOf(SONGS, SETLISTS, FILES))

    /**
     * Above this, a file sync moves is moved alone rather than beside others: a few attachments at 50 MB each, held
     * whole, are more than a phone's heap.
     */
    const val LARGE_FILE_THRESHOLD = ImportLimits.MAX_TEXT_FILE_SIZE

    /**
     * The largest document of the cloud folder's own (`preferences.json`, the shared links list) sync downloads: the
     * library's own limit for a text file. A `preferences.json` of thousands of songs with chords and tags, and a shared
     * links list of thousands of shares, stay far under it, so it never refuses a document of a real library.
     */
    const val MAX_FOLDER_DOCUMENT_SIZE = ImportLimits.MAX_TEXT_FILE_SIZE
}
