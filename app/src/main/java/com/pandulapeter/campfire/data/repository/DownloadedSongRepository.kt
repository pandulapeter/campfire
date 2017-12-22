package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.util.enqueueCall
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlin.properties.Delegates

/**
 * Wraps caching and updating of [DownloadedSong] objects.
 */
class DownloadedSongRepository(
    private val dataStorageManager: DataStorageManager,
    private val fileStorageManager: FileStorageManager,
    private val networkManager: NetworkManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.downloadedSongCache) { _, _, new -> dataStorageManager.downloadedSongCache = new }
    private val downloadQueue = mutableListOf<String>()

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.DownloadedSongsUpdated(getDownloadedSongIds()))
    }

    fun getDownloadCacheSize(): Long {
        var totalSizeInBytes = 0L
        dataSet.keys.forEach {
            totalSizeInBytes += fileStorageManager.getFileSize(it)
        }
        return totalSizeInBytes
    }

    fun getDownloadedSongIds(): List<String> = dataSet.keys.toList()

    fun getDownloadedSong(songId: String) = dataSet[songId]

    fun isSongDownloaded(songId: String) = dataSet.containsKey(songId)

    fun isSongLoading(songId: String) = downloadQueue.contains(songId)

    fun removeSongFromDownloads(songId: String) {
        if (isSongDownloaded(songId)) {
            fileStorageManager.deleteDownloadedSongText(songId)
            dataSet = dataSet.toMutableMap().apply { remove(songId) }
            notifySubscribers(UpdateType.SongRemovedFromDownloads(songId))
        }
    }

    private fun addSongToDownloads(downloadedSong: DownloadedSong, songDetail: SongDetail) {
        addSongToDownloadsWithoutNotifications(downloadedSong, songDetail) {
            notifySubscribers(UpdateType.SongAddedToDownloads(songDetail.id))
        }
    }

    fun clearDownloads() {
        downloadQueue.clear()
        getDownloadedSongIds().forEach {
            fileStorageManager.deleteDownloadedSongText(it)
        }
        dataSet = dataSet.toMutableMap().apply { clear() }
        notifySubscribers(UpdateType.AllDownloadsRemoved)
    }

    //TODO: It's not great that we need to know the version of the song before downloading it, but this is a backend API limitation.
    fun downloadSong(songInfo: SongInfo, onSuccess: (String) -> Unit = {}, onFailure: () -> Unit = {}) {
        dataSet[songInfo.id]?.let {
            getDownloadedSongText(songInfo.id)?.let {
                onSuccess(it)
                return
            }
        }
        if (!isSongLoading(songInfo.id)) {
            downloadQueue.add(songInfo.id)
        }
        notifySubscribers(UpdateType.DownloadStarted(songInfo.id))
        networkManager.service.getSong(songInfo.id).enqueueCall(
            onSuccess = {
                addSongToDownloadsWithoutNotifications(DownloadedSong(it.id, songInfo.version ?: 0), it) {
                    notifySubscribers(UpdateType.DownloadSuccessful(songInfo.id))
                    onSuccess(it.song)
                }
            },
            onFailure = {
                notifySubscribers(UpdateType.DownloadFailed(songInfo.id))
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

    private fun addSongToDownloadsWithoutNotifications(downloadedSong: DownloadedSong, songDetail: SongDetail, action: () -> Unit) {
        if (downloadQueue.contains(downloadedSong.id)) {
            downloadQueue.remove(downloadedSong.id)
        }
        async(UI) {
            async(CommonPool) {
                fileStorageManager.saveDownloadedSongText(downloadedSong.id, songDetail.song)
            }.await()
            action()
        }
        dataSet = dataSet.toMutableMap().apply { put(downloadedSong.id, downloadedSong) }
    }
}