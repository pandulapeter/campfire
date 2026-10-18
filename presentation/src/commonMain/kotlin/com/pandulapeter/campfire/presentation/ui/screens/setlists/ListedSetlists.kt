/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

/**
 * The setlists a list shows, with whether the songs they were resolved against are the whole library.
 *
 * @param isLibraryRead Carried with the setlists rather than read from `LibraryState.isLoading`, because their songs
 *   are indexed on another dispatcher and arrive after the read is over: a list told it was loaded by that would
 *   animate the library landing in it.
 * @param filterKey The folded search query the setlists were narrowed by, empty for none: carried with them for the same
 *   reason, since the list arrives frames after the text is typed, and the list scrolls back to the top when the one
 *   built for another query arrives rather than when the text changes.
 */
data class ListedSetlists(
    val setlists: List<SetlistWithSongs>,
    val isLibraryRead: Boolean,
    val filterKey: String,
) {

    companion object {
        val Empty = ListedSetlists(setlists = emptyList(), isLibraryRead = false, filterKey = "")
    }
}
