package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.database.SongDatabase
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.networking.NetworkManager
import com.pandulapeter.campfire.util.enqueueCall

class SongRepository(private val networkManager: NetworkManager, private val songDatabase: SongDatabase) : Repository<List<Song>>() {

    override val data = mutableListOf<Song>()

    fun isDataAvailable() = data.isNotEmpty()

    fun updateData(onFailure: () -> Unit = {}) {
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                data.clear()
                data.addAll(it)
                notifySubscribers()
            },
            onFailure = {
                onFailure()
            })
    }
}