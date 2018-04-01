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
) : Repository<SongRepository.Subscriber>() {

    private val data = mutableListOf<Song>()
    private var isLoading = true
        set(value) {
            if (field != value) {
                field = value
                subscribers.forEach { it.onLoadingStateChanged(value) }
            }
        }

    init {
        async(UI) {
            async(CommonPool) {
                songDatabase.songDao().getAll()
            }.await().let {
                data.swap(it)
                if (System.currentTimeMillis() - preferenceDatabase.lastUpdateTimestamp > UPDATE_LIMIT) {
                    updateData()
                } else {
                    isLoading = false
                }
                notifyDataChanged()
            }
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onDataChanged(data)
        subscriber.onLoadingStateChanged(isLoading)
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
                subscribers.forEach { it.onError() }
            })
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onDataChanged(data) }

    interface Subscriber {

        fun onDataChanged(data: List<Song>)

        fun onLoadingStateChanged(isLoading: Boolean)

        fun onError()
    }

    companion object {
        private const val UPDATE_LIMIT = 24 * 60 * 60 * 1000
    }
}