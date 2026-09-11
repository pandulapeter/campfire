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
 * What an import would do to the library, worked out before a single file is written.
 *
 * The file name is a song's identity, so a file whose name the library already has is the one thing an import
 * cannot decide on its own: it is either the same song arriving a second time, which nobody needs two copies of, or
 * a different song under a name that is taken, which is a question for whoever started the import. Working that out
 * up front is what makes the question askable once for a whole archive instead of once per file.
 */
data class ImportPlan(
    val songs: List<SongEntry> = emptyList(),
    val setlists: List<SetlistEntry> = emptyList(),
    /** Files that are neither a song nor a setlist, could not be decoded, or held nothing to import. */
    val skippedFileNames: List<String> = emptyList(),
) {

    /** True while nothing about this import needs answering, which is every import into an untouched name. */
    val hasConflicts get() = songs.any { it.status == Status.CONFLICTING } || setlists.any { it.status == Status.CONFLICTING }

    val isEmpty get() = songs.isEmpty() && setlists.isEmpty()

    /** The counts and names the conflict dialog is built from, so that the UI never walks the entries itself. */
    val summary
        get() = Summary(
            newSongCount = songs.count { it.status == Status.NEW },
            newSetlistCount = setlists.count { it.status == Status.NEW },
            duplicateCount = songs.count { it.status == Status.IDENTICAL } + setlists.count { it.status == Status.IDENTICAL },
            skippedCount = skippedFileNames.size,
            conflictingFileNames = songs.filter { it.status == Status.CONFLICTING }.map { it.fileName } +
                setlists.filter { it.status == Status.CONFLICTING }.map { it.fileName },
        )

    data class SongEntry(
        /** The name it wants in the library, already derived the way the storage layer derives one. */
        val fileName: String,
        val text: String,
        val status: Status,
        /**
         * The name of the file it arrived in, which is what a setlist travelling with it points at. Null for one
         * song of a collection, since the file it came from named none of them.
         */
        val sourceFileName: String?,
    )

    data class SetlistEntry(
        val fileName: String,
        val setlist: Setlist,
        val status: Status,
    )

    /** What the library already has under the name the entry wants. */
    enum class Status {
        /** The name is free, so the file goes in as it is. */
        NEW,

        /**
         * The name is taken by something that is already exactly this, so the import has nothing to do. Songs are
         * compared by their text and setlists by their title and entries - never by the stored document, which
         * carries a priority the import assigns itself.
         */
        IDENTICAL,

        /** The name is taken by something else, which only the user can decide about. */
        CONFLICTING,
    }

    data class Summary(
        val newSongCount: Int,
        val newSetlistCount: Int,
        val duplicateCount: Int,
        val skippedCount: Int,
        val conflictingFileNames: List<String>,
    )
}

/** What an import does with the entries whose name the library has given to something else. */
enum class ImportConflictResolution {

    /**
     * The incoming file lands next to the one it collides with, numbered the way every name the app writes itself is:
     * `tukorfurogep-arviz_2`, rather than the " (2)" a file arriving under a name of someone else's making gets.
     */
    KEEP_BOTH,

    /** The incoming file is written over the one the library has. The only way anything is ever overwritten. */
    REPLACE,

    /** The incoming file is not imported at all, and the library keeps what it had. */
    SKIP,
}
