/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song

/**
 * Points the entries of a setlist translated from another app's set at songs found by title, where the set named a song
 * the archive it came in did not hold ([ImportedFile.setlistSongTitles]): a song of the batch first, its own picked
 * file's before another's, then a song of the library. Titles compare as file names do (`LibraryFiles.identityKey`: case
 * and Unicode form folded). Where several songs share a title, one this setlist has not pointed at yet is taken first,
 * so a set naming two songs of one title finds both. An entry nothing answers stays as it is: a missing song.
 */
internal object SetlistSongTitles {

    /**
     * [setlist], from the picked file [origin], with its entries found by [titles]. [batchFileNames] are the names its
     * entries resolve among the batch's songs ([ImportPlanner.SongFileNames.forOrigin]), which need nothing found.
     */
    fun resolved(
        setlist: Setlist,
        titles: Map<String, String>,
        origin: Int?,
        batchFileNames: Map<String, String>,
        songs: List<ImportPlan.SongEntry>,
        librarySongs: List<Song>,
    ): Setlist {
        if (titles.isEmpty()) return setlist
        val taken = mutableSetOf<String>()
        val batchSongs = songs.filter { it.sourceFileName != null }.sortedBy { it.origin != origin }
        val libraryFileNames = librarySongs.sortedBy { it.fileName }
        return setlist.copy(
            entries = setlist.entries.map { entry ->
                val title = titles[entry.songFileName]?.takeIf { entry.songFileName !in batchFileNames } ?: return@map entry
                val key = LibraryFiles.identityKey(title.trim())
                val found = batchSongs.filter { LibraryFiles.identityKey(it.title.trim()) == key }.mapNotNull { it.sourceFileName }.untakenFirst(taken)
                    ?: libraryFileNames.filter { LibraryFiles.identityKey(it.title.trim()) == key }.map { it.fileName }.untakenFirst(taken)
                    ?: return@map entry
                taken += found
                entry.copy(songFileName = found)
            },
        )
    }

    private fun List<String>.untakenFirst(taken: Set<String>) = firstOrNull { it !in taken } ?: firstOrNull()
}
