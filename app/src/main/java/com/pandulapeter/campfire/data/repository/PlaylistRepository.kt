package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.SongDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class PlaylistRepository(private val songDatabase: SongDatabase) : Repository<PlaylistRepository.Subscriber>() {
    private val data = mutableListOf<Playlist>()
    private var isCacheLoaded = false

    init {
        refreshDataSet()
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onPlaylistsUpdated(data)
    }

    fun isCacheLoaded() = isCacheLoaded

    fun deleteAllPlaylists() {
        data.clear()
        async(UI) {
            async(CommonPool) { songDatabase.playlistDao().deleteAll() }.await()
            notifyDataChanged()
        }
    }

    private fun refreshDataSet() {
        async(UI) {
            async(CommonPool) {
                songDatabase.playlistDao().getAll()
            }.await().let { newData ->
                val finalNewData = newData.toMutableList()
                val favorites = Playlist(
                    id = Playlist.FAVORITES_ID,
                    order = 0
                )
                if (newData.isEmpty()) {
                    async(CommonPool) {
                        songDatabase.playlistDao().insert(favorites)
                    }.await()
                    finalNewData.add(favorites)
                }
                data.swap(finalNewData.sortedBy { it.order })
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onPlaylistsUpdated(data) }

    interface Subscriber {

        fun onPlaylistsUpdated(playlists: List<Playlist>)
    }
}