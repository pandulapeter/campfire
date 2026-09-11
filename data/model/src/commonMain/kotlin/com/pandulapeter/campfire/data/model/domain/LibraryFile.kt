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
 * The two kinds of file the library is made of. Sync works one level above songs and setlists - it moves files, not
 * models - so it needs a way to say which of the two directories a file name belongs to.
 */
enum class LibraryFileKind(val id: String) {
    SONG("songs"),
    SETLIST("setlists");

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
