package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager

/**
 * Wraps caching and updating of [Playlist] objects.
 */
class PlaylistRepository(private val preferenceStorageManager: PreferenceStorageManager,
                         private val songInfoRepository: SongInfoRepository) : Repository() {
    private val dataSet = mutableListOf<Playlist>()
}