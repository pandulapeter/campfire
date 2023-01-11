package com.pandulapeter.campfire.domain.models

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.model.domain.Song

data class ScreenData(
    val collections: List<Collection>,
    val databases: List<Database>,
    val playlists: List<Playlist>,
    val songs: List<Song>
)