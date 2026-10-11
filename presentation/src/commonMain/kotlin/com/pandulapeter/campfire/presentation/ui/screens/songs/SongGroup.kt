/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import com.pandulapeter.campfire.data.model.domain.Song

/**
 * @param header Null for the results of a search, which are ranked rather than filed under anything.
 * @param copiedFileNames The songs of the Selection section that are still listed in their own section too, having
 *   been ticked since the list was last refreshed ([withSelectionLeading]). A copy is an item of its own to the grid.
 */
data class SongGroup(
    val header: SongGroupHeader?,
    val songs: List<Song>,
    val copiedFileNames: Set<String> = emptySet(),
) {
    /** The key of the grid item that shows [song] in this group: its own one, or its copy's. */
    fun itemKey(song: Song) = if (song.fileName in copiedFileNames) "$SONG_COPY_ITEM_KEY_PREFIX${song.fileName}" else songItemKey(song)
}

/** What [SongGroup.itemKey] puts in front of the file name of a copy, a prefix no [songItemKey] can start with. */
private const val SONG_COPY_ITEM_KEY_PREFIX = "copy_song_"
