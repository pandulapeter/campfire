package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.util.enqueueCall
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of [DownloadedSong] objects.
 */
class DownloadedSongRepository(
    private val dataStorageManager: DataStorageManager,
    private val fileStorageManager: FileStorageManager,
    private val networkManager: NetworkManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.downloadedSongCache) { _: KProperty<*>, old: Map<String, DownloadedSong>, new: Map<String, DownloadedSong> ->
        if (old != new) {
            notifySubscribers(UpdateType.DownloadedSongsUpdated(getDownloadedSongIds()))
            //TODO: If only a single line has been changed, we should not rewrite the entire map.
            dataStorageManager.downloadedSongCache = new
        }
    }
    private var downloadQueue = mutableListOf<String>()

    fun getDownloadedSongIds(): List<String> = dataSet.keys.toList()

    fun getDownloadedSong(id: String) = dataSet[id]

    fun isSongDownloaded(id: String) = dataSet.containsKey(id)

    fun isSongLoading(id: String) = downloadQueue.contains(id)

    fun removeSongFromDownloads(id: String) {
        if (isSongDownloaded(id)) {
            fileStorageManager.deleteDownloadedSongText(id)
            dataSet = dataSet.toMutableMap().apply { remove(id) }
            notifySubscribers()
        }
    }

    //TODO: It's not great that we need to know the version of the song before downloading it, but this is a backend API limitation.
    fun downloadSong(songInfo: SongInfo, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        dataSet[songInfo.id]?.let {
            getDownloadedSongText(songInfo.id)?.let {
                onSuccess(it)
                return
            }
        }
        downloadQueue.add(songInfo.id)
        networkManager.service.getSong(songInfo.id).enqueueCall(
            onSuccess = {
                downloadQueue.remove(songInfo.id)
                fileStorageManager.saveDownloadedSongText(it.id, it.song)
                dataSet = dataSet.toMutableMap().apply { put(it.id, DownloadedSong(it.id, songInfo.version ?: 0)) }
                onSuccess(it.song)
            },
            onFailure = {
                downloadQueue.remove(songInfo.id)
                onFailure()
            }
        )
    }

    private fun getDownloadedSongText(id: String) = if (isSongDownloaded(id)) {
        fileStorageManager.loadDownloadedSongText(id)
    } else {
        null
    }
}