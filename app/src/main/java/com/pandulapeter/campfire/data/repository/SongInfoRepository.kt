package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.util.enqueueCall

/**
 * Wraps caching and updating of [SongInfo] objects.
 */
class SongInfoRepository(private val storageManager: StorageManager, private val networkManager: NetworkManager) {

    private val dataSet = storageManager.library.toMutableList()

    private fun isCacheInvalid() = System.currentTimeMillis() - storageManager.lastLibraryUpdate > CACHE_VALIDITY_LIMIT_IN_MILLIS

    fun getLibrary(changeListener: ChangeListener<List<SongInfo>>, forceRefresh: Boolean) {
        changeListener.onNext(dataSet)
        if (forceRefresh || isCacheInvalid()) {
            networkManager.service.getLibrary().enqueueCall(
                onSuccess = {
                    dataSet.clear()
                    dataSet.addAll(it)
                    storageManager.library = it
                    storageManager.lastLibraryUpdate = System.currentTimeMillis()
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

    companion object {
        private const val CACHE_VALIDITY_LIMIT_IN_MILLIS = 1000 * 60 * 60 * 24
    }
}