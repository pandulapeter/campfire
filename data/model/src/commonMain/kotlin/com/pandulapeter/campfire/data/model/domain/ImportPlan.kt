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
    /** Files the import would have looked inside but did not read, because they are larger than [ImportLimits] allows. */
    val oversizedFileNames: List<String> = emptyList(),
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
            oversizedCount = oversizedFileNames.size,
            conflictingFileNames = songs.filter { it.status == Status.CONFLICTING }.map { it.fileName } +
                setlists.filter { it.status == Status.CONFLICTING }.map { it.fileName },
        )

    data class SongEntry(
        /**
         * The name it wants in the library, already derived the way the storage layer derives one — or, for an
         * [Status.IDENTICAL] entry, the name of the library file that already is this song, which may be a numbered
         * sibling of the name it would have wanted.
         */
        val fileName: String,
        val text: String,
        val status: Status,
        /**
         * The name of the file it arrived in, which is what a setlist travelling with it points at. Null for one
         * song of a collection, since the file it came from named none of them.
         */
        val sourceFileName: String?,
        /**
         * For an [Status.IDENTICAL] entry that repeats an earlier song of the same import rather than a library file:
         * the place of that song in [ImportPlan.songs]. Where it ends up is only known once it has been written, so
         * the plan can name the entry but not the file.
         */
        val repeatedEntryIndex: Int? = null,
    )

    data class SetlistEntry(
        val fileName: String,
        /**
         * The setlist as it arrived, its entries naming the song files of the import. Where those songs end up is
         * only known once they have been written, and the status is decided on the setlist pointing there.
         */
        val setlist: Setlist,
        val status: Status,
        /** The name of the file it arrived in, which the setlist is planned again under once the songs are written. */
        val sourceFileName: String,
    )

    /** What the library already has under the name the entry wants. */
    enum class Status {
        /**
         * The name is free, taken only by an earlier file of the same import, or taken by a library file that the same
         * import brings back unchanged — which it is not the library's to give up — so the storage layer numbers it if needed.
         */
        NEW,

        /**
         * The library — under this name or a numbered sibling of it — or an earlier file of the same import already
         * is exactly this, so the import has nothing to do. Songs are compared by their text and setlists by their
         * title and entries, pointing at the songs where the import puts them - never by the stored document, which
         * carries a priority the import assigns itself.
         */
        IDENTICAL,

        /**
         * The name is taken in the library by something else that the import does not bring back itself, which only the
         * user can decide about.
         */
        CONFLICTING,
    }

    data class Summary(
        val newSongCount: Int,
        val newSetlistCount: Int,
        val duplicateCount: Int,
        val skippedCount: Int,
        val oversizedCount: Int,
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
