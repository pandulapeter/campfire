package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.feature.home.SongInfoAdapter
import com.pandulapeter.campfire.util.enqueueCall

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(storageManager: StorageManager, private val networkManager: NetworkManager) {

    val adapter = SongInfoAdapter()
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val isLoading = ObservableBoolean(false)

    init {
        update()
    }

    fun update() {
        isLoading.set(true)
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                adapter.songInfoList = it
                isLoading.set(false)
            },
            onFailure = {
                shouldShowErrorSnackbar.set(true)
                isLoading.set(false)
            })
    }
}
