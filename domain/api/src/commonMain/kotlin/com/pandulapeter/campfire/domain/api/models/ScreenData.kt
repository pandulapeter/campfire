/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag

data class ScreenData(
    /**
     * Every setlist, in the order the preferences ask for and with the archived ones last. Unfiltered on purpose:
     * the screen decides whether it shows the archived ones, while everything that writes to a setlist looks it up
     * in this list and has to find one that is merely out of sight.
     */
    val setlists: List<Setlist>,
    /** The library, filtered and sorted the way the user preferences ask for. */
    val songs: List<Song>,
    /**
     * Every tag the library uses, the most used one first, as the filter controls offer them. Counted before the tag
     * filter is applied and after every other one, so that selecting a tag does not empty the list of tags one could
     * select next. A tag that is selected stays on the list even where the language filter has counted it down to
     * nothing, since a filter that is on has to be visible to be turned off.
     */
    val tags: List<Tag>,
    /**
     * Every language the library sings in, the most used one first and the songs that declare none
     * ([SongLanguage.UNKNOWN]) last, whatever their number. Counted the way [tags] are, and with the tag filter
     * applied, so that the two filter groups narrow each other rather than emptying each other.
     */
    val languages: List<SongLanguage>,
    /**
     * The whole library, whether or not the filters hide a song and in no particular order. The setlists are read
     * from here rather than from [songs]: a setlist is the list somebody wrote down, so it shows what it holds
     * whatever the song list is currently narrowed to, and an entry missing from here is a file that is really gone.
     */
    val unfilteredSongs: List<Song>,
)
