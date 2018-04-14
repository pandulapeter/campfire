package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.SongDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import java.util.*

class PlaylistRepository(private val songDatabase: SongDatabase) : Repository<PlaylistRepository.Subscriber>() {
    private val data = mutableListOf<Playlist>()
    private var isCacheLoaded = false
    val cache get() = data.toList()

    init {
        refreshDataSet()
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onPlaylistsUpdated(data)
    }

    fun isCacheLoaded() = isCacheLoaded

    fun deleteAllPlaylists() {
        data.swap(data.filter { it.id == Playlist.FAVORITES_ID })
        async(UI) { async(CommonPool) { songDatabase.playlistDao().deleteAll() }.await() }
        notifyDataChanged()
    }

    fun deletePlaylist(playlistId: String) {
        data.swap(data.filter { it.id != playlistId })
        async(UI) { async(CommonPool) { songDatabase.playlistDao().delete(playlistId) }.await() }
        notifyDataChanged()
    }

    fun createNewPlaylist(title: String) {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            title = title,
            order = data.size
        )
        data.add(playlist)
        notifyDataChanged()
        async(UI) { async(CommonPool) { songDatabase.playlistDao().insert(playlist) }.await() }
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
                    async(CommonPool) { songDatabase.playlistDao().insert(favorites) }.await()
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