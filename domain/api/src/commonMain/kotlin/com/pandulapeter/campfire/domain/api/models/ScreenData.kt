package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.model.domain.UserPreferences

data class ScreenData(
    val setlists: List<Setlist>,
    val songs: List<Song>,
    val userPreferences: UserPreferences,
    /** Just the urls: the text of a song is read when it is opened, see `GetSongDetailsUseCase`. */
    val downloadedSongUrls: Set<String>,
    val transpositions: Map<TranspositionKey, Int>
)
