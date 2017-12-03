package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.storage.DataStorageManager
import java.util.Collections

/**
 * Wraps caching and updating of [Playlist] objects.
 */
class PlaylistRepository(private val dataStorageManager: DataStorageManager,
                         private val songInfoRepository: SongInfoRepository) : Repository() {

    fun getFavoriteSongs() = dataStorageManager.favorites.mapNotNull { id -> songInfoRepository.getLibrarySongs().find { id == it.id } }

    fun isSongFavorite(id: String) = dataStorageManager.favorites.contains(id)

    fun addSongToFavorites(id: String, position: Int? = null) {
        if (!isSongFavorite(id)) {
            dataStorageManager.favorites = dataStorageManager.favorites.toMutableList().apply {
                if (position == null) add(id) else add(position, id)
            }
            notifySubscribers()
        }
    }

    fun removeSongFromFavorites(id: String) {
        if (isSongFavorite(id)) {
            dataStorageManager.favorites = dataStorageManager.favorites.toMutableList().apply { remove(id) }
            notifySubscribers()
        }
    }

    fun setFavorites(songIds: List<String>) {
        dataStorageManager.favorites = songIds
        notifySubscribers()
    }

    fun shuffleFavorites() {
        val list = dataStorageManager.favorites.toMutableList()
        if (list.size > SongInfoRepository.SHUFFLE_LIMIT) {
            val newList = list.toMutableList()
            while (newList == list) {
                Collections.shuffle(newList)
            }
            dataStorageManager.favorites = newList
            notifySubscribers()
        }
    }
}