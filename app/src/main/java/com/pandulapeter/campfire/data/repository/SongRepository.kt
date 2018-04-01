package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.database.PreferenceDatabase
import com.pandulapeter.campfire.data.database.SongDatabase
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.networking.NetworkManager
import com.pandulapeter.campfire.util.enqueueCall
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class SongRepository(
    private val preferenceDatabase: PreferenceDatabase,
    private val networkManager: NetworkManager,
    private val songDatabase: SongDatabase
) : Repository<List<Song>>() {

    override val data = mutableListOf<Song>()
    var isLoading = true
        set(value) {
            if (field != value) {
                field = value
                notifyLoadingStateChanged()
            }
        }

    init {
        async(UI) {
            async(CommonPool) {
                songDatabase.songDao().getAll()
            }.await().let {
                data.swap(it)
                if (shouldUpdateData()) {
                    updateData()
                } else {
                    isLoading = false
                }
                notifyDataChanged()
            }
        }
    }

    fun updateData() {
        isLoading = true
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                data.swap(it)
                async(CommonPool) { songDatabase.songDao().updateData(data) }
                isLoading = false
                notifyDataChanged()
                preferenceDatabase.lastUpdateTimestamp = System.currentTimeMillis()
            },
            onFailure = {
                isLoading = false
                notifyError()
            })
    }

    private fun shouldUpdateData() = System.currentTimeMillis() - preferenceDatabase.lastUpdateTimestamp > UPDATE_LIMIT

    companion object {
        private const val UPDATE_LIMIT = 24 * 60 * 60 * 1000
    }
}