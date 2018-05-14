package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.repository.shared.BaseRepository
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import java.util.*

class PlaylistRepository(private val database: Database) : BaseRepository<PlaylistRepository.Subscriber>() {
    private val data = mutableListOf<Playlist>()
    private var isCacheLoaded = false
    var hiddenPlaylistId: String? = null
        set(value) {
            field = value
            notifyDataChanged()
        }
    val cache get() = data.toList()

    init {
        refreshDataSet()
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onPlaylistsUpdated(data)
    }

    fun isCacheLoaded() = isCacheLoaded

    fun isSongInAnyPlaylist(songId: String): Boolean {
        data.forEach {
            if (isSongInPlaylist(it.id, songId)) {
                return true
            }
        }
        return false
    }

    fun isSongInPlaylist(playlistId: String, songId: String) = data.find { it.id == playlistId }?.songIds?.contains(songId) ?: false

    fun addSongToPlaylist(playlistId: String, songId: String) {
        async(UI) {
            var shouldNotify = false
            async(CommonPool) {
                shouldNotify = !isSongInAnyPlaylist(songId)
                val playlist = data.find { it.id == playlistId }
                if (playlist?.songIds?.contains(songId) == false) {
                    playlist.songIds.add(songId)
                }
                playlist
            }.await()?.let { playlist ->
                if (shouldNotify) {
                    subscribers.forEach { it.onSongAddedToPlaylistForTheFirstTime(songId) }
                }
                async(CommonPool) { database.playlistDao().insert(playlist) }.await()
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        async(UI) {
            var shouldNotify = false
            async(CommonPool) {
                val playlist = data.find { it.id == playlistId }
                if (playlist?.songIds?.contains(songId) == true) {
                    playlist.songIds.remove(songId)
                }
                shouldNotify = !isSongInAnyPlaylist(songId)
                playlist
            }.await()?.let { playlist ->
                if (shouldNotify) {
                    subscribers.forEach { it.onSongRemovedFromAllPlaylists(songId) }
                }
                async(CommonPool) { database.playlistDao().insert(playlist) }.await()
            }
        }
    }

    fun deleteAllPlaylists() {
        data.swap(data.filter { it.id == Playlist.FAVORITES_ID })
        async(UI) { async(CommonPool) { database.playlistDao().deleteAll() }.await() }
        notifyDataChanged()
    }

    fun deletePlaylist(playlistId: String) {
        data.swap(data.filter { it.id != playlistId })
        async(UI) { async(CommonPool) { database.playlistDao().delete(playlistId) }.await() }
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
        async(UI) { async(CommonPool) { database.playlistDao().insert(playlist) }.await() }
    }

    fun updatePlaylistTitle(playlistId: String, title: String) {
        val playlist = data.find { it.id == playlistId }
        playlist?.let {
            it.title = title
            subscribers.forEach { notifyDataChanged() }
            async(UI) { async(CommonPool) { database.playlistDao().insert(it) }.await() }
        }
    }

    fun updatePlaylistOrder(playlistId: String, order: Int) {
        val playlist = data.find { it.id == playlistId }
        playlist?.let {
            it.order = order
            subscribers.forEach { it.onPlaylistOrderChanged(data) }
            async(UI) { async(CommonPool) { database.playlistDao().insert(it) }.await() }
        }
    }

    fun updatePlaylistSongIds(playlistId: String, songsIds: MutableList<String>) {
        data.find { it.id == playlistId }?.let { playlist ->
            playlist.songIds = songsIds
            async(CommonPool) { database.playlistDao().insert(playlist) }
        }
    }

    private fun refreshDataSet() {
        async(UI) {
            async(CommonPool) {
                database.playlistDao().getAll()
            }.await().let { newData ->
                val finalNewData = newData.toMutableList()
                val favorites = Playlist(
                    id = Playlist.FAVORITES_ID,
                    order = 0
                )
                if (newData.isEmpty()) {
                    async(CommonPool) { database.playlistDao().insert(favorites) }.await()
                    finalNewData.add(favorites)
                }
                data.swap(finalNewData)
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onPlaylistsUpdated(data) }

    interface Subscriber {

        fun onPlaylistsUpdated(playlists: List<Playlist>)

        fun onPlaylistOrderChanged(playlists: List<Playlist>)

        fun onSongAddedToPlaylistForTheFirstTime(songId: String)

        fun onSongRemovedFromAllPlaylists(songId: String)
    }
}