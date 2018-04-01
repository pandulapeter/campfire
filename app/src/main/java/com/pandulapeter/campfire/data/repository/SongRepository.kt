package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.database.SongDatabase
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.networking.NetworkManager
import com.pandulapeter.campfire.util.enqueueCall
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class SongRepository(private val networkManager: NetworkManager, private val songDatabase: SongDatabase) : Repository<List<Song>>() {

    override val data = mutableListOf<Song>()

    init {
        async(UI) {
            async(CommonPool) {
                songDatabase.songDao().getAll()
            }.await().let {
                data.clear()
                data.addAll(it)
                notifySubscribers()
            }
        }
    }

    fun isDataAvailable() = data.isNotEmpty()

    fun updateData(onFailure: () -> Unit = {}) {
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                data.clear()
                data.addAll(it)
                notifySubscribers()
                async(CommonPool) { songDatabase.songDao().updateData(data) }
            },
            onFailure = { onFailure() })
    }
}