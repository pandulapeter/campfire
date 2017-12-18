package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.DataStorageManager

/**
 * Wraps caching and updating of [DownloadedSong] objects.
 */
class DownloadedSongRepository(
    private val dataStorageManager: DataStorageManager,
    private val networkManager: NetworkManager) : Repository() {
    private var dataSet = dataStorageManager.downloads
        set(value) {
            if (field != value) {
                field = value
                dataStorageManager.downloads = value
                notifySubscribers()
            }
        }

    fun getDownloadedSongs() = dataStorageManager.downloads

    fun isSongDownloaded(id: String) = getDownloadedSongs().map { it.id }.contains(id)

    //TODO: Also save the SongDetail object.
    fun addSongToDownloads(songInfo: SongInfo) {
        dataStorageManager.downloads = getDownloadedSongs().filter { it.id != songInfo.id }.toMutableList().apply { add(DownloadedSong(songInfo.id, songInfo.version ?: 0)) }
        notifySubscribers()
    }

    fun removeSongFromDownloads(id: String) {
        if (isSongDownloaded(id)) {
            dataStorageManager.downloads = getDownloadedSongs().filter { it.id != id }
            notifySubscribers()
        }
    }
}