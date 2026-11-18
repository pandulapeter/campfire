/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song

/**
 * [setlists] as the app shows them, each entry naming its song the way the library's file does. A setlist may spell a
 * song as another case or Unicode form of its file's name (written by hand, or on a device whose file system folds
 * them), which is the same file ([LibraryFiles.identityKey]) but no exact match, and everything that looks an entry's
 * song up by file name - the setlist's rows, its pager, its overrides, the address of a page - reads these. Only this
 * view is respelt: every write goes to the setlist as its file has it and finds the entry by identity, so the file keeps
 * its own spelling.
 *
 * Two entries that turn out to name one song keep the first, as a setlist naming one spelling twice is read, since the
 * rows and the pager are keyed by the song's file name.
 */
internal fun setlistsSpeltAsLibrary(setlists: List<Setlist>, songs: List<Song>): List<Setlist> {
    val fileNames = songs.mapTo(hashSetOf()) { it.fileName }
    // Folding every name of the library is skipped where each entry is found as it is, which is nearly always.
    val byIdentity by lazy { songs.associate { LibraryFiles.identityKey(it.fileName) to it.fileName } }
    return setlists.map { setlist ->
        if (setlist.entries.all { it.songFileName in fileNames }) {
            setlist
        } else {
            setlist.copy(
                entries = setlist.entries.map { entry ->
                    if (entry.songFileName in fileNames) {
                        entry
                    } else {
                        byIdentity[LibraryFiles.identityKey(entry.songFileName)]?.let { entry.copy(songFileName = it) } ?: entry
                    }
                }.distinctBy { it.songFileName },
            )
        }
    }
}
