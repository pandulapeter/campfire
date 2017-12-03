package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.storage.StorageManager

/**
 * Wraps caching and updating of [Playlist] objects.
 */
class PlaylistRepository(private val storageManager: StorageManager) : Repository() {
    private val dataSet = mutableListOf<Playlist>()
}