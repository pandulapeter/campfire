package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences

data class ScreenData(
    val setlists: List<Setlist>,
    /** The library, filtered and sorted the way the user preferences ask for. */
    val songs: List<Song>,
    /**
     * The file name of every song in the library, whether or not the filters hide it. Setlist entries are resolved
     * against this: a song the "show songs without chords" filter is hiding is not a song whose file went missing.
     */
    val songFileNames: Set<String>,
    val userPreferences: UserPreferences
)
