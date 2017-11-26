package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.util.enqueueCall
import java.util.Collections

/**
 * Wraps caching and updating of [SongInfo] objects.
 *
 * TODO: Implement subscription model.
 */
class SongInfoRepository(private val storageManager: StorageManager, private val networkManager: NetworkManager) {

    private var dataSet = storageManager.cloudCache
        get() {
            if (System.currentTimeMillis() - storageManager.lastCacheUpdateTimestamp > CACHE_VALIDITY_LIMIT) {
                updateDataSet()
            }
            return field
        }

    //TODO Make this observable.
    var isLoading = false

    fun updateDataSet() {
        isLoading = true
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                isLoading = false
                dataSet = it
                storageManager.cloudCache = dataSet
                storageManager.lastCacheUpdateTimestamp = System.currentTimeMillis()
                //TODO: Notify listeners.
            },
            onFailure = {
                isLoading = false
                //TODO: Notify listeners.
            })
    }

    fun getCloudSongs() = dataSet.toList()

    fun getDownloadedSongs() = storageManager.downloaded.mapNotNull { id -> dataSet.find { id == it.id } }

    fun isSongDownloaded(id: String) = storageManager.downloaded.contains(id)

    fun addSongToDownloaded(id: String) {
        if (!isSongDownloaded(id)) {
            storageManager.downloaded = storageManager.downloaded.toMutableList().apply { add(id) }
        }
    }

    fun removeSongFromDownloaded(id: String) {
        removeSongFromFavorites(id)
        if (isSongDownloaded(id)) {
            storageManager.downloaded = storageManager.downloaded.toMutableList().apply { remove(id) }
        }
    }

    fun getFavoriteSongs() = storageManager.favorites.mapNotNull { id -> dataSet.find { id == it.id } }

    fun isSongFavorite(id: String) = storageManager.favorites.contains(id)

    fun addSongToFavorites(id: String, position: Int? = null) {
        if (!isSongFavorite(id)) {
            storageManager.favorites = storageManager.favorites.toMutableList().apply {
                if (position == null) {
                    add(id)
                } else {
                    add(position, id)
                }
            }
        }
    }

    fun removeSongFromFavorites(id: String) {
        if (isSongFavorite(id)) {
            storageManager.favorites = storageManager.favorites.toMutableList().apply { remove(id) }
        }
    }

    fun swapSongFavoritesPositions(originalPosition: Int, targetPosition: Int) {
        if (originalPosition != targetPosition) {
            storageManager.favorites = storageManager.favorites.toMutableList().apply {
                if (originalPosition < targetPosition) {
                    for (i in originalPosition until targetPosition) {
                        Collections.swap(this, i, i + 1)
                    }
                } else {
                    for (i in originalPosition downTo targetPosition + 1) {
                        Collections.swap(this, i, i - 1)
                    }
                }
            }
        }
    }

    fun shuffleFavorites() {
        val list = storageManager.favorites.toMutableList()
        if (list.size > SHUFFLE_LIMIT) {
            val newList = list.toMutableList()
            while (newList == list) {
                Collections.shuffle(newList)
            }
            storageManager.favorites = newList
        }
    }

    companion object {
        const val SHUFFLE_LIMIT = 2
        private const val CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}