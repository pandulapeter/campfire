package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.repository.shared.BaseRepository
import com.pandulapeter.campfire.util.enqueueCall
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

class SongDetailRepository(
    private val networkManager: NetworkManager,
    private val database: Database
) : BaseRepository<SongDetailRepository.Subscriber>() {
    private val data = mutableListOf<SongDetailMetadata>()
    private val downloadQueue = mutableListOf<String>()
    private var isCacheLoaded = false

    init {
        refreshDataSet()
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onSongDetailRepositoryUpdated(data)
        subscriber.onSongDetailRepositoryDownloadQueueChanged(downloadQueue)
    }

    fun isCacheLoaded() = isCacheLoaded

    fun getSongDetail(song: Song, shouldDelay: Boolean = false) {
        if (!isSongDownloading(song.id)) {
            if (isSongDownloaded(song.id)) {
                launch(UI) {
                    async(CommonPool) { database.songDetailDao().get(song.id) }.await().let { songDetail ->
                        if (songDetail == null) {
                            deleteSong(song.id)
                            getSongDetail(song)
                        } else {
                            subscribers.forEach { it.onSongDetailRepositoryDownloadSuccess(songDetail) }
                        }
                    }
                }
                if (getSongVersion(song.id) == song.version ?: 0) {
                    return
                }
            }
            downloadQueue.add(song.id)
            notifyDownloadQueChanged()
            val started = System.currentTimeMillis()
            networkManager.service.getSong(song.id).enqueueCall(
                onSuccess = { songDetail ->
                    songDetail.version = song.version ?: 0
                    launch(UI) {
                        async(CommonPool) { database.songDetailDao().insert(songDetail) }.await()
                        if (shouldDelay && System.currentTimeMillis() - started < 300) {
                            delay(300)
                        }
                        refreshDataSet()
                        subscribers.forEach { it.onSongDetailRepositoryDownloadSuccess(songDetail) }
                        downloadQueue.remove(song.id)
                        notifyDownloadQueChanged()
                    }
                },
                onFailure = {
                    downloadQueue.remove(song.id)
                    notifyDownloadQueChanged()
                    subscribers.forEach { it.onSongDetailRepositoryDownloadError(song) }
                }
            )
        }
    }

    fun isSongDownloading(songId: String) = downloadQueue.contains(songId)

    fun isSongDownloaded(songId: String) = data.find { it.id == songId } != null

    fun getSongVersion(songId: String) = data.find { it.id == songId }?.version ?: 0

    fun deleteSong(songId: String) {
        data.swap(data.filter { it.id != songId })
        launch(UI) {
            async(CommonPool) { database.songDetailDao().delete(songId) }.await()
            notifyDataChanged()
        }
    }

    fun deleteAllSongs() {
        data.clear()
        launch(UI) {
            async(CommonPool) { database.songDetailDao().deleteAll() }.await()
            notifyDataChanged()
        }
    }

    private fun refreshDataSet() {
        launch(UI) {
            async(CommonPool) {
                database.songDetailDao().getAllMetadata()
            }.await().let {
                data.swap(it)
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onSongDetailRepositoryUpdated(data) }

    private fun notifyDownloadQueChanged() = subscribers.forEach { it.onSongDetailRepositoryDownloadQueueChanged(downloadQueue) }

    interface Subscriber {

        fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>)

        fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail)

        fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>)

        fun onSongDetailRepositoryDownloadError(song: Song)
    }
}