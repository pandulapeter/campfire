package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.util.enqueueCall

/**
 * Wraps caching and updating of [SongInfo] objects.
 */
class SongInfoRepository(private val storageManager: StorageManager, private val networkManager: NetworkManager) {

    private val libraryDataSet = storageManager.library.toMutableList()

    private fun isCacheInvalid() = System.currentTimeMillis() - storageManager.lastLibraryUpdate > LIBRARY_CACHE_VALIDITY_LIMIT

    fun getLibrary(changeListener: ChangeListener<List<SongInfo>>, forceRefresh: Boolean) {
        changeListener.onNext(libraryDataSet)
        if (forceRefresh || isCacheInvalid()) {
            networkManager.service.getLibrary().enqueueCall(
                onSuccess = {
                    libraryDataSet.clear()
                    libraryDataSet.addAll(it)
                    storageManager.library = it
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

    fun getDownloaded() = storageManager.downloaded

    fun downloadSong(songInfo: SongInfo) {
        storageManager.downloaded = storageManager.downloaded.toMutableList().apply { if (!contains(songInfo)) add(songInfo) }
    }

    fun deleteDownloadedSong(songInfo: SongInfo) {
        storageManager.downloaded = storageManager.downloaded.toMutableList().apply { if (contains(songInfo)) remove(songInfo) }
    }

    companion object {
        private const val LIBRARY_CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}