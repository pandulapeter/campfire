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

/**
 * The kinds of file the library is made of. Sync works one level above songs and setlists - it moves files, not
 * models - so it needs a way to say which of the directories a file name belongs to.
 *
 * [FILE] is what songs show besides their text: pictures, named by their content (see [LibraryFiles.isLibraryImageName]),
 * kept while a song names them. The folder is named for files rather than images so that other kinds kept with a song
 * could later share its storage, sync, and pruning without renaming a folder every user's cloud already holds. A version
 * without this entry leaves the folder alone on both sides, which is what lets it share a cloud folder with this one.
 */
enum class LibraryFileKind(val id: String) {
    SONG("songs"),
    SETLIST("setlists"),
    FILE("files");

    /**
     * Whether [name] is a file of this kind, going by its name: the right extension, and not a hidden file, see
     * [LibraryFiles.isSongFileName]. Both of the folders sync compares are
     * ones the user can open - the library folder on the desktop, the app folder of their cloud storage
     * everywhere - so either may hold whatever else they keep there, and that is left where it is. Every listing
     * of either side has to ask this one question: a file that one side lists and the other leaves out looks to
     * a sync run exactly like a file that was deleted there.
     */
    fun matches(name: String) = when (this) {
        SONG -> LibraryFiles.isSongFileName(name)
        SETLIST -> LibraryFiles.isSetlistFileName(name)
        FILE -> LibraryFiles.isLibraryImageName(name)
    }

    /**
     * Whether files of this kind are what the user keeps rather than what follows from it: a picture is kept, synced,
     * and deleted with the songs that name it, so the questions about deleting much of a library count only these.
     */
    val isDocument get() = this != FILE

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * One file of the library as the sync engine sees it: a name inside a [kind], plus what the file system could tell
 * about it. [lastModified] is 0 where the platform cannot say (the web), which is why nothing decides whether a file
 * has changed by looking at it - see `SyncPlanner`.
 */
data class LibraryFile(
    val kind: LibraryFileKind,
    val name: String,
    val size: Long,
    val lastModified: Long,
)
