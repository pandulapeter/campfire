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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.state.SongLookup

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

/** [songDetailsSongsOf] for [destination], remembered. */
@Composable
internal fun rememberLibrarySongs(destination: CampfireDestination.SongDetails, songLookup: SongLookup, songsBeingRenamed: Map<String, Song>) =
    remember(destination, songLookup, songsBeingRenamed) {
        // A song whose file is being renamed is still this screen's song. The library drops the old name as the file
        // moves and the back stack is rewritten a few writes later, and in between the destination names a song the
        // library does not hold: resolving it to the song as it was keeps the pages - and their count - exactly
        // where they are, instead of this screen closing itself or a setlist settling on the next song. The pager's key
        // is the file name, so a name the destination repeats - whatever built it, a saved back stack included - would
        // be a repeated key and a crash: each song is one page.
        songDetailsSongsOf(destination.songFileNames, songLookup, songsBeingRenamed)
    }
