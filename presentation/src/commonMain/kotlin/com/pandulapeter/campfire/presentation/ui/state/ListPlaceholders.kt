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

import com.pandulapeter.campfire.presentation.ui.state.songs.SongListState
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.screens.setlists.ListedSetlists
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups

/**
 * What the song list shows instead of songs, null while it has some, see [SongListState.songsPlaceholder]. Told by the
 * counts [songGroups] was built from, never by [screenData]'s songs, which arrive frames before the list does;
 * [screenData] only says whether a library with no songs is still loading or failed to be read.
 */
internal fun songListPlaceholder(screenData: DataState<ScreenData>, songGroups: SongGroups, isImporting: Boolean): Placeholder? = when {
    songGroups.groups.isNotEmpty() -> null
    !songGroups.isLibraryRead -> Placeholder.LOADING
    // The library itself, not the filtered list: a library that only holds songs the filters hide is not an
    // empty one, and offering to create a first song there would be answering a question nobody asked.
    songGroups.librarySongCount == 0 -> screenData.emptyPlaceholder(Placeholder.NO_SONGS, isImporting)
    songGroups.filteredSongCount == 0 -> Placeholder.ALL_SONGS_HIDDEN
    else -> Placeholder.NO_MATCHING_SONGS
}

/**
 * What the setlists screen shows instead of setlists, null while it has some, see
 * [SetlistsController.setlistsPlaceholder]. Told by the counts [listed] was built from, as [songListPlaceholder] is.
 *
 * @param listed The setlists the screen lists, its search applied.
 */
internal fun setlistListPlaceholder(screenData: DataState<ScreenData>, listed: ListedSetlists, isImporting: Boolean): Placeholder? = when {
    listed.setlists.isNotEmpty() -> null
    !listed.isLibraryRead -> Placeholder.LOADING
    listed.libraryCount == 0 -> screenData.emptyPlaceholder(Placeholder.NO_SETLISTS, isImporting)
    listed.unsearchedCount == 0 -> Placeholder.ALL_SETLISTS_HIDDEN
    else -> Placeholder.NO_MATCHING_SETLISTS
}

/**
 * Whether a list counting [listedCount] items of the library was built from an older library than [screenData]'s,
 * by [libraryCount] of it. A placeholder only answers for a list built from the library it is shown with, so while
 * this holds it keeps the value it had: both counts come from the same [screenData], so they always meet again. No
 * data at all (a first read, a failure with nothing read) is never behind.
 */
internal fun isListBehind(screenData: DataState<ScreenData>, listedCount: Int, libraryCount: (ScreenData) -> Int): Boolean =
    screenData.data?.let { libraryCount(it) != listedCount } == true
