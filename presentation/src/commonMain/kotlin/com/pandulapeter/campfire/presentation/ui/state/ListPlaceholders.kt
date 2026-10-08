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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups

/** What the song list shows instead of songs, null while it has some, see [SongListState.songsPlaceholder]. */
internal fun songListPlaceholder(screenData: DataState<ScreenData>, songGroups: SongGroups, isImporting: Boolean): Placeholder? {
    val data = screenData.data
    return when {
        songGroups.groups.isNotEmpty() -> null
        // The library itself, not the filtered list: a library that only holds songs the filters hide is not an
        // empty one, and offering to create a first song there would be answering a question nobody asked.
        data == null || data.unfilteredSongs.isEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SONGS, isImporting)
        data.songs.isEmpty() -> Placeholder.ALL_SONGS_HIDDEN
        else -> Placeholder.NO_MATCHING_SONGS
    }
}

/**
 * What the setlists screen shows instead of setlists, null while it has some, see
 * [SetlistsController.setlistsPlaceholder].
 *
 * @param setlistsWithSongs The setlists the screen lists, its search applied.
 * @param visibleSetlists The same before the search, the archived filter applied.
 */
internal fun setlistListPlaceholder(
    screenData: DataState<ScreenData>,
    setlistsWithSongs: List<SetlistWithSongs>,
    visibleSetlists: List<SetlistWithSongs>,
    isImporting: Boolean,
): Placeholder? = when {
    setlistsWithSongs.isNotEmpty() -> null
    screenData.data?.setlists.isNullOrEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SETLISTS, isImporting)
    visibleSetlists.isEmpty() -> Placeholder.ALL_SETLISTS_HIDDEN
    else -> Placeholder.NO_MATCHING_SETLISTS
}
