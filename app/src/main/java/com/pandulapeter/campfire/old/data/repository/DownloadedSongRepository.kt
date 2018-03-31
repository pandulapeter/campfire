package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.old.data.model.DownloadedSong
import com.pandulapeter.campfire.old.data.model.SongDetail
import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import com.pandulapeter.campfire.old.data.storage.FileStorageManager
import com.pandulapeter.campfire.old.networking.NetworkManager
import com.pandulapeter.campfire.old.util.enqueueCall
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
    private val networkManager: NetworkManager
) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.downloadedSongCache) { _, _, new -> dataStorageManager.downloadedSongCache = new }
    private val downloadQueue = mutableListOf<String>()

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.DownloadedSongsUpdated(getDownloadedSongIds()))
    }

    fun getDownloadSize(songId: String) = fileStorageManager.getFileSize(songId)

    fun getDownloadCacheSize(): Long {
        var totalSizeInBytes = 0L
        dataSet.keys.forEach {
            totalSizeInBytes += fileStorageManager.getFileSize(it)
        }
        return totalSizeInBytes
    }

    fun getDownloadedSongIds(): List<String> = dataSet.keys.toList()

    fun getSongDownloadedState(songId: String) = if (isSongLoading(songId)) {
        DownloadState.Downloading
    } else {
        if (isSongDownloaded(songId)) {
            if (areThereUpdatesToTheSong(songId)) {
                DownloadState.Downloaded.Deprecated
            } else {
                DownloadState.Downloaded.UpToDate
            }
        } else {
            if (isSongNew(songId)) {
                DownloadState.NotDownloaded.New
            } else {
                DownloadState.NotDownloaded.Old
            }
        }
    }

    fun isSongDownloaded(songId: String) = dataSet.containsKey(songId)

    fun isSongLoading(songId: String) = downloadQueue.contains(songId)

    fun areThereUpdatesToTheSong(songId: String) = false //TODO: Missing functionality.

    fun isSongNew(songId: String) = false //TODO: Missing functionality.

    fun removeSongFromDownloads(songId: String) {
        if (isSongDownloaded(songId)) {
            fileStorageManager.deleteDownloadedSongText(songId)
            dataSet = dataSet.toMutableMap().apply { remove(songId) }
            notifySubscribers(UpdateType.SongRemovedFromDownloads(songId))
        }
    }

    fun downloadedItemCount() = dataSet.keys.size

    fun clearDownloads() {
        downloadQueue.clear()
        getDownloadedSongIds().forEach {
            fileStorageManager.deleteDownloadedSongText(it)
        }
        dataSet = dataSet.toMutableMap().apply { clear() }
        notifySubscribers(UpdateType.AllDownloadsRemoved)
    }

    fun startSongDownload(songInfo: SongInfo, onFailure: () -> Unit = {}) {
        //TODO: Check that the updating logic actually works. Looks like the cache is never updated.
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
                addSongToDownloadsWithoutNotifications(DownloadedSong(it.id, songInfo.version ?: 0), it) {
                    notifySubscribers(UpdateType.Download.Successful(songInfo.id, it.song))
                }
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

    sealed class DownloadState {

        sealed class NotDownloaded : DownloadState() {
            object Old : NotDownloaded()
            object New : NotDownloaded()
        }

        object Downloading : DownloadState()

        sealed class Downloaded : DownloadState() {
            object UpToDate : Downloaded()
            object Deprecated : Downloaded()
        }
    }
}