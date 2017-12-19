package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.storage.DataStorageManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of [Playlist] objects.
 */
class PlaylistRepository(private val dataStorageManager: DataStorageManager,
                         private val downloadedSongRepository: DownloadedSongRepository) : Repository<Map<String, Playlist>>() {
    override var dataSet by Delegates.observable(dataStorageManager.playlists) { _: KProperty<*>, old: Map<String, Playlist>, new: Map<String, Playlist> ->
        if (old != new) {
            notifySubscribers(UpdateType.PlaylistsUpdated(getPlaylists()))
            //TODO: If only a single line has been changed, we should not rewrite the entire map.
            //TODO: If only a single line has been changed, send a more specific notify event.
            dataStorageManager.playlists = new
        }
    }

    fun getPlaylists(): List<Playlist> = dataSet.values.toList()

    fun getPlaylist(playlistId: Int) = dataSet[playlistId.toString()]

    fun newPlaylist(title: String) {
        var id = 0
        while (dataSet.containsKey(id.toString())) {
            id++
        }
        dataSet = dataSet.toMutableMap().apply { put(id.toString(), Playlist.Custom(id, title)) }
    }

    fun getDownloadedSongIdsFromPlaylist(playlistId: Int) = getPlaylist(playlistId)?.songIds?.filter { downloadedSongRepository.isSongDownloaded(it) } ?: listOf()

    fun isSongInPlaylist(playlistId: Int, songId: String) = getPlaylist(playlistId)?.songIds?.contains(songId) == true

    fun isSongInAnyPlaylist(songId: String): Boolean {
        getPlaylists().forEach {
            if (isSongInPlaylist(it.id, songId)) {
                return true
            }
        }
        return false
    }

    fun addSongToPlaylist(playlistId: Int, songId: String, position: Int? = null) {
        if (!isSongInPlaylist(playlistId, songId)) {
            dataSet = dataSet.toMutableMap().apply {
                getPlaylist(playlistId)?.apply {
                    songIds.apply { if (!contains(songId)) if (position == null) add(songId) else add(position, songId) }
                }?.let { put(playlistId.toString(), it) }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        if (isSongInPlaylist(playlistId, songId)) {
            dataSet = dataSet.toMutableMap().apply {
                getPlaylist(playlistId)?.apply {
                    songIds.apply { remove(songId) }
                }?.let { put(playlistId.toString(), it) }
            }
        }
    }

    fun renamePlaylist(playlistId: Int, title: String) {
        if (playlistId != Playlist.FAVORITES_ID) {
            dataSet = dataSet.toMutableMap().apply {
                getPlaylist(playlistId)?.songIds?.let {
                    put(playlistId.toString(), Playlist.Custom(playlistId, title, it))
                }
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        if (dataSet.containsKey(playlistId.toString())) {
            dataSet = dataSet.toMutableMap().apply { remove(playlistId.toString()) }
        }
    }

    fun updatePlaylist(playlistId: Int, songIds: List<String>) {
        dataSet = dataSet.toMutableMap().apply {
            getPlaylist(playlistId)?.apply {
                this.songIds.apply {
                    clear()
                    addAll(songIds)
                }
            }?.let { put(playlistId.toString(), it) }
        }
    }
}