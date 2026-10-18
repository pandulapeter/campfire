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

import com.pandulapeter.campfire.data.model.song.Song

/**
 * @param header Null for the results of a search, which are ranked rather than filed under anything.
 * @param isSelection Whether these are the ticked songs that lead the list, which stay told apart from the rest by a
 *   divider even where an open search has taken their header away.
 * @param copiedFileNames The songs of the Selection section that are still listed in their own section too, having
 *   been ticked since the list was last refreshed ([withSelectionLeading]). A copy is an item of its own to the grid.
 * @param leadingGap The empty cells before the first song, an item of their own to the grid, which an open search puts
 *   there to keep the card that was at the top left of the list in its column ([withoutSectionHeaders]).
 */
data class SongGroup(
    val header: SongGroupHeader?,
    val songs: List<Song>,
    val copiedFileNames: Set<String> = emptySet(),
    val isSelection: Boolean = header == SongGroupHeader.Selection,
    val leadingGap: Int = 0,
) {
    /** The key of the grid item that shows [song] in this group: its own one, or its copy's. */
    fun itemKey(song: Song) = if (song.fileName in copiedFileNames) "$SONG_COPY_ITEM_KEY_PREFIX${song.fileName}" else songItemKey(song)

    /**
     * Where the song whose [itemKey] is [key] is in [songs], or -1. The key is taken apart rather than every song's key
     * built to compare with it, since a tick looks a card up across the whole library.
     */
    fun indexOfItem(key: Any): Int {
        val text = key as? String ?: return -1
        val isCopy = text.startsWith(SONG_COPY_ITEM_KEY_PREFIX)
        val fileName = when {
            isCopy -> text.removePrefix(SONG_COPY_ITEM_KEY_PREFIX)
            text.startsWith(SONG_ITEM_KEY_PREFIX) -> text.removePrefix(SONG_ITEM_KEY_PREFIX)
            else -> return -1
        }
        return songs.indexOfFirst { it.fileName == fileName && (it.fileName in copiedFileNames) == isCopy }
    }
}

internal fun songItemKey(song: Song) = "$SONG_ITEM_KEY_PREFIX${song.fileName}"

/** What [songItemKey] puts in front of a song's file name. */
private const val SONG_ITEM_KEY_PREFIX = "song_"

/** What [SongGroup.itemKey] puts in front of the file name of a copy, a prefix no [songItemKey] can start with. */
private const val SONG_COPY_ITEM_KEY_PREFIX = "copy_song_"

/** What a song's card is to the grid. */
internal const val SONG_CONTENT_TYPE = "song"
