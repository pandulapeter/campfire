package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import kotlin.properties.Delegates

/**
 * Wraps caching and updating of [Playlist] objects.
 */
class PlaylistRepository(private val dataStorageManager: DataStorageManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.playlists) { _, _, new -> dataStorageManager.playlists = new }

    init {
        if (!dataSet.keys.contains(Playlist.FAVORITES_ID.toString())) {
            dataSet = dataSet.toMutableMap().apply { put(Playlist.FAVORITES_ID.toString(), Playlist(Playlist.FAVORITES_ID)) }
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.PlaylistsUpdated(getPlaylists()))
    }

    fun getPlaylists(): List<Playlist> = dataSet.values.toList()

    fun getPlaylist(playlistId: Int) = dataSet[playlistId.toString()]

    private fun getPlaylistSongIds(playlistId: Int) = dataSet[playlistId.toString()]?.songIds ?: listOf()

    fun isSongInPlaylist(playlistId: Int, songId: String) = getPlaylistSongIds(playlistId).contains(songId)

    fun isSongInAnyPlaylist(songId: String): Boolean {
        getPlaylists().forEach {
            if (it.songIds.contains(songId)) {
                return true
            }
        }
        return false
    }

    fun addSongToPlaylist(playlistId: Int, songId: String, position: Int? = null) {
        if (!isSongInPlaylist(playlistId, songId)) {
            dataSet = dataSet.toMutableMap().apply {
                getPlaylist(playlistId)?.let {
                    put(
                        playlistId.toString(),
                        Playlist(
                            playlistId,
                            it.title,
                            it.songIds.toMutableList().apply { if (!contains(songId)) if (position == null) add(songId) else add(position, songId) })
                    )
                }
            }
            notifySubscribers(UpdateType.SongAddedToPlaylist(playlistId, songId, position ?: dataSet.values.size-1))
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        if (isSongInPlaylist(playlistId, songId)) {
            val songIds = getPlaylistSongIds(playlistId)
            val position = songIds.indexOf(songId)
            dataSet = dataSet.toMutableMap().apply {
                getPlaylist(playlistId)?.let {
                    put(playlistId.toString(), Playlist(playlistId, it.title, songIds.toMutableList().apply { removeAt(position) }))
                }
            }
            notifySubscribers(UpdateType.SongRemovedFromPlaylist(playlistId, songId, position))
        }
    }
}