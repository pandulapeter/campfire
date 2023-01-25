package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences

data class ScreenData(
    val databases: List<Database>,
    val setlists: List<Setlist>,
    val songs: List<Song>,
    val userPreferences: UserPreferences
)