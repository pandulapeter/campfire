/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.domain.Song

/**
 * The whole library by file name, together with whether it has been read, as one value: see `LibraryState.songLookup`.
 *
 * @param isLibraryRead False while the library is being read for the first time, as `LibraryState.isLoading` says.
 */
internal data class SongLookup(
    val songsByFileName: Map<String, Song>,
    val isLibraryRead: Boolean,
) {
    companion object {
        val Empty = SongLookup(emptyMap(), isLibraryRead = false)
    }
}

/**
 * The songs a details screen pages through, or null while it cannot say yet: before the library has been read,
 * only a destination whose every song is already in the lookup is answered, since a partial batch holding some of a
 * setlist's songs would lay the pager out on the wrong page (a saved pager restores its page index, and keeps it by key
 * as the rest arrives), and an empty answer would close the screen.
 */
internal fun songDetailsSongsOf(songFileNames: List<String>, lookup: SongLookup, songsBeingRenamed: Map<String, Song>): List<Song>? {
    val songs = songFileNames.mapNotNull { lookup.songsByFileName[it] ?: songsBeingRenamed[it] }.distinctBy { it.fileName }
    return songs.takeIf { lookup.isLibraryRead || songs.size == songFileNames.distinct().size }
}
