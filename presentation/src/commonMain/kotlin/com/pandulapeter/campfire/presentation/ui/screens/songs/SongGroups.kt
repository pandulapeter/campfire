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

/**
 * The song list with the filter, the sort and the query it was built for: two equal lists for two filters are two
 * values. The song list keeps a tapped row in place once the list built for [filterKey] arrives, and with the key read
 * from anywhere else a filter that left every song where it was would never deliver that list, leaving the row to be
 * put back in place by whatever changed the list next.
 *
 * @param orderKey The sort and the query alone, which put different songs at the top of the list and so send it back
 * there; a filter only takes songs out or puts them back, and the list stays where it was reading.
 * @param isLibraryRead Whether these are the songs of the whole library rather than of a read still under way, carried
 * with them because the songs are put together on another dispatcher and arrive after `LibraryState.isLoading` says
 * the read is over: a list told it was loaded by that would animate the songs landing in it.
 * @param librarySongCount The songs in the library the list was built from, and [filteredSongCount] those of them the
 * filters let through, before the search: counted in the same pass as [groups], so that the placeholder tells its empty
 * states apart by the list alone and never pairs it with a library that is newer.
 */
data class SongGroups(
    val filterKey: String,
    val orderKey: String,
    val groups: List<SongGroup>,
    val isLibraryRead: Boolean,
    val librarySongCount: Int,
    val filteredSongCount: Int,
)
