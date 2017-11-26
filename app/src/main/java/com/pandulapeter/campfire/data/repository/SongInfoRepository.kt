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

    private val dataSet = storageManager.cloudCache.toMutableList()

    private fun isCacheInvalid() = System.currentTimeMillis() - storageManager.lastCacheUpdateTimestamp > CACHE_VALIDITY_LIMIT

    fun getCloudSongs(changeListener: ChangeListener<List<SongInfo>>, forceRefresh: Boolean = false) {
        changeListener.onNext(dataSet)
        if (forceRefresh || isCacheInvalid()) {
            networkManager.service.getLibrary().enqueueCall(
                onSuccess = {
                    dataSet.clear()
                    dataSet.addAll(it)
                    storageManager.cloudCache = dataSet
                    storageManager.lastCacheUpdateTimestamp = System.currentTimeMillis()
                    changeListener.onNext(dataSet)
                    changeListener.onComplete()
                },
                onFailure = {
                    changeListener.onError()
                })
        } else {
            changeListener.onComplete()
        }
    }

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
        Collections.shuffle(list)
        storageManager.favorites = list
    }

    companion object {
        private const val CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}