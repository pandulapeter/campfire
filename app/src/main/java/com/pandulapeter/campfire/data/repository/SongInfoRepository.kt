package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.util.enqueueCall
import java.util.Collections

/**
 * Wraps caching and updating of [SongInfo] objects.
 */
class SongInfoRepository(private val storageManager: StorageManager, private val networkManager: NetworkManager) {

    private val libraryDataSet = storageManager.library.toMutableList()

    private fun isCacheInvalid() = System.currentTimeMillis() - storageManager.lastLibraryUpdate > LIBRARY_CACHE_VALIDITY_LIMIT

    fun getCloudSongs(changeListener: ChangeListener<List<SongInfo>>, forceRefresh: Boolean = false) {
        changeListener.onNext(libraryDataSet)
        if (forceRefresh || isCacheInvalid()) {
            networkManager.service.getLibrary().enqueueCall(
                onSuccess = {
                    libraryDataSet.clear()
                    libraryDataSet.addAll(it)
                    storageManager.library = libraryDataSet
                    storageManager.lastLibraryUpdate = System.currentTimeMillis()
                    changeListener.onNext(libraryDataSet)
                    changeListener.onComplete()
                },
                onFailure = {
                    changeListener.onError()
                })
        } else {
            changeListener.onComplete()
        }
    }

    fun getDownloadedSongs() = storageManager.downloaded

    fun addSongToDownloaded(songInfo: SongInfo) {
        storageManager.downloaded = getDownloadedSongs().toMutableList().apply { if (!contains(songInfo)) add(songInfo) }
    }

    fun removeSongFromDownloaded(songInfo: SongInfo) {
        removeSongFromFavorites(songInfo)
        storageManager.downloaded = getDownloadedSongs().toMutableList().apply { if (contains(songInfo)) remove(songInfo) }
    }

    fun getFavoriteIds() = storageManager.favorites

    fun addSongToFavorites(songInfo: SongInfo, position: Int? = null) {
        storageManager.favorites = storageManager.favorites.toMutableList().apply {
            if (!contains(songInfo.id)) {
                if (position == null) {
                    add(songInfo.id)
                } else {
                    add(position, songInfo.id)
                }
            }
        }
    }

    fun removeSongFromFavorites(songInfo: SongInfo) {
        storageManager.favorites = storageManager.favorites.toMutableList().apply { if (contains(songInfo.id)) remove(songInfo.id) }
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

    companion object {
        private const val LIBRARY_CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}