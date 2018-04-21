package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.old.data.model.DownloadedSong
import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import com.pandulapeter.campfire.old.data.storage.FileStorageManager
import com.pandulapeter.campfire.util.enqueueCall
import kotlin.properties.Delegates

/**
 * Wraps caching and updating of [DownloadedSong] objects.
 */
class DownloadedSongRepository(
    private val dataStorageManager: DataStorageManager,
    private val fileStorageManager: FileStorageManager,
    private val networkManager: NetworkManager
) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.downloadedSongCache) { _, _, new -> dataStorageManager.downloadedSongCache = new }
    private val downloadQueue = mutableListOf<String>()

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.DownloadedSongsUpdated())
    }

    fun isSongDownloaded(songId: String) = dataSet.containsKey(songId)

    fun isSongLoading(songId: String) = downloadQueue.contains(songId)

    fun startSongDownload(songInfo: SongInfo, onFailure: () -> Unit = {}) {
        dataSet[songInfo.id]?.let {
            getDownloadedSongText(songInfo.id)?.let {
                notifySubscribers(UpdateType.Download.Successful(songInfo.id, it))
                return
            }
        }
        if (!isSongLoading(songInfo.id)) {
            downloadQueue.add(songInfo.id)
        }
        notifySubscribers(UpdateType.Download.Started(songInfo.id))
        networkManager.service.getSong(songInfo.id).enqueueCall(
            onSuccess = {
            },
            onFailure = {
                notifySubscribers(UpdateType.Download.Failed(songInfo.id))
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