package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.repository.shared.BaseRepository
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    fun getPlaylistCountForSong(songId: String): Int {
        var count = 0
        data.forEach {
            if (isSongInPlaylist(it.id, songId)) {
                count++
            }
        }
        return count
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        GlobalScope.launch(WORKER) {
            var shouldNotify = false
            async(UI) {
                shouldNotify = !isSongInAnyPlaylist(songId)
                val playlist = data.find { it.id == playlistId }
                if (playlist?.songIds?.contains(songId) == false) {
                    playlist.songIds.add(songId)
                }
                playlist
            }.await()?.also { playlist ->
                if (shouldNotify) {
                    subscribers.forEach { it.onSongAddedToPlaylistForTheFirstTime(songId) }
                }
                launch(WORKER) { database.playlistDao().insert(playlist) }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        GlobalScope.launch(WORKER) {
            var shouldNotify = false
            async(UI) {
                val playlist = data.find { it.id == playlistId }
                if (playlist?.songIds?.contains(songId) == true) {
                    playlist.songIds.remove(songId)
                }
                shouldNotify = !isSongInAnyPlaylist(songId)
                playlist
            }.await()?.also { playlist ->
                if (shouldNotify) {
                    subscribers.forEach { it.onSongRemovedFromAllPlaylists(songId) }
                }
                launch(WORKER) { database.playlistDao().insert(playlist) }
            }
        }
    }

    fun deleteAllPlaylists() {
        data.swap(data.filter { it.id == Playlist.FAVORITES_ID })
        notifyDataChanged()
        GlobalScope.launch(WORKER) { database.playlistDao().deleteAll() }
    }

    fun deletePlaylist(playlistId: String) {
        data.swap(data.filter { it.id != playlistId })
        notifyDataChanged()
        GlobalScope.launch(WORKER) { database.playlistDao().delete(playlistId) }
    }

    fun createNewPlaylist(title: String) {
        data.sortedBy { it.order }.forEachIndexed { index, playlist -> playlist.order = index }
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            title = title,
            order = data.size
        )
        data.add(playlist)
        notifyDataChanged()
        GlobalScope.launch(WORKER) {
            database.playlistDao().deleteAll()
            data.forEach { database.playlistDao().insert(it) }
        }
    }

    fun updatePlaylistTitle(playlistId: String, title: String) {
        val playlist = data.find { it.id == playlistId }
        playlist?.also {
            it.title = title
            subscribers.forEach { notifyDataChanged() }
            GlobalScope.launch(WORKER) { database.playlistDao().insert(it) }
        }
    }

    fun updatePlaylistOrder(playlistId: String, order: Int) {
        val playlist = data.find { it.id == playlistId }
        playlist?.also {
            it.order = order
            subscribers.forEach { it.onPlaylistOrderChanged(data) }
            GlobalScope.launch(WORKER) { database.playlistDao().insert(it) }
        }
    }

    fun updatePlaylistSongIds(playlistId: String, songsIds: MutableList<String>) {
        data.find { it.id == playlistId }?.let { playlist ->
            playlist.songIds = songsIds
            GlobalScope.launch(WORKER) { database.playlistDao().insert(playlist) }
        }
    }

    private fun refreshDataSet() {
        GlobalScope.launch(UI) {
            async(WORKER) { database.playlistDao().getAll() }.await().let { newData ->
                val finalNewData = newData.toMutableList()
                val favorites = Playlist(
                    id = Playlist.FAVORITES_ID,
                    order = 0
                )
                if (newData.isEmpty()) {
                    launch(WORKER) { database.playlistDao().insert(favorites) }
                    finalNewData.add(favorites)
                }
                data.swap(finalNewData)
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = GlobalScope.launch(UI) { subscribers.forEach { it.onPlaylistsUpdated(data) } }

    interface Subscriber {

        fun onPlaylistsUpdated(playlists: List<Playlist>)

        fun onPlaylistOrderChanged(playlists: List<Playlist>)

        fun onSongAddedToPlaylistForTheFirstTime(songId: String)

        fun onSongRemovedFromAllPlaylists(songId: String)
    }
}