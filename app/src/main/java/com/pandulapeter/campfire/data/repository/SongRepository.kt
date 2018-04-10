package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.persistence.SongDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
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
    private var isCacheLoaded = false
    private var isLoading = true
        set(value) {
            if (field != value) {
                field = value
                subscribers.forEach { it.onSongRepositoryLoadingStateChanged(value) }
            }
        }

    init {
        async(UI) {
            async(CommonPool) {
                songDatabase.songDao().getAll()
            }.await().let {
                data.swap(it)
                isCacheLoaded = true
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
        if (!isLoading) {
            subscriber.onSongRepositoryDataUpdated(data)
        }
        subscriber.onSongRepositoryLoadingStateChanged(isLoading)
        if (!isLoading && data.isEmpty()) {
            subscriber.onSongRepositoryUpdateError()
        }
    }

    fun isCacheLoaded() = isCacheLoaded

    fun updateData() {
        isLoading = true
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = { newData ->
                if (data.isNotEmpty()) {
                    newData.forEach { song ->
                        if (data.find { it.id == song.id } == null) {
                            song.isNew = true
                        }
                    }
                }
                data.swap(newData)
                async(CommonPool) { songDatabase.songDao().updateData(data) }
                isLoading = false
                notifyDataChanged()
                preferenceDatabase.lastUpdateTimestamp = System.currentTimeMillis()
            },
            onFailure = {
                isLoading = false
                subscribers.forEach { it.onSongRepositoryUpdateError() }
            })
    }

    fun onSongOpened(songId: String) {
        data.find { it.id == songId }?.let {
            if (it.isNew) {
                it.isNew = false
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onSongRepositoryDataUpdated(data) }

    interface Subscriber {

        fun onSongRepositoryDataUpdated(data: List<Song>)

        fun onSongRepositoryLoadingStateChanged(isLoading: Boolean)

        fun onSongRepositoryUpdateError()
    }

    companion object {
        private const val UPDATE_LIMIT = 24 * 60 * 60 * 1000
    }
}