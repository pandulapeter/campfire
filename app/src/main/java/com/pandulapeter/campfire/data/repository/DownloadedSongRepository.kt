package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.util.enqueueCall

/**
 * Wraps caching and updating of [DownloadedSong] objects.
 */
class DownloadedSongRepository(
    private val dataStorageManager: DataStorageManager,
    private val fileStorageManager: FileStorageManager,
    private val networkManager: NetworkManager) : Repository() {
    private var dataSet = dataStorageManager.downloads
        set(value) {
            if (field != value) {
                field = value
                dataStorageManager.downloads = value
                notifySubscribers()
            }
        }

    fun getDownloadedSongs() = dataSet

    fun isSongDownloaded(id: String) = getDownloadedSongs().map { it.id }.contains(id)

    fun getDownloadedSongText(id: String) = fileStorageManager.loadDownloadedSongText(id)

    fun removeSongFromDownloads(id: String) {
        if (isSongDownloaded(id)) {
            fileStorageManager.deleteDownloadedSongText(id)
            dataSet = getDownloadedSongs().filter { it.id != id }
            notifySubscribers()
        }
    }

    fun downloadSong(songInfo: SongInfo, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val cachedSongInfo = dataSet.find { it.id == songInfo.id }
        if (cachedSongInfo != null) {
            onSuccess(getDownloadedSongText(songInfo.id))
        } else {
            networkManager.service.getSong(songInfo.id).enqueueCall(
                onSuccess = {
                    fileStorageManager.saveDownloadedSongText(it.id, it.song)
                    dataSet = getDownloadedSongs().filter { it.id != songInfo.id }.toMutableList().apply { add(DownloadedSong(songInfo.id, songInfo.version ?: 0)) }
                    onSuccess(it.song)
                },
                onFailure = { onFailure() }
            )
        }
    }
}