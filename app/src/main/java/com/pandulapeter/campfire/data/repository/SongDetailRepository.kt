package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.database.SongDetailDatabase
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.networking.NetworkManager
import com.pandulapeter.campfire.util.enqueueCall
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class SongDetailRepository(
    private val networkManager: NetworkManager,
    private val songDetailDatabase: SongDetailDatabase
) : Repository<SongDetailRepository.Subscriber>() {
    private var isInitialized = false
    private val data: MutableList<Pair<String, Int>> = mutableListOf()

    init {
        refreshDataSet()
    }

    fun downloadSong(song: Song) {
        networkManager.service.getSong(song.id).enqueueCall(
            onSuccess = { songDetail ->
                async(UI) {
                    async(CommonPool) { songDetailDatabase.songDetailDao().insert(songDetail) }.await()
                    refreshDataSet()
                    subscribers.forEach { it.onSuccess(songDetail) }
                }
            },
            onFailure = { subscribers.forEach { it.onError(song) } }
        )
    }

    fun isSongDownloaded(songId: String) = data.find { it.first == songId } != null

    fun getSongVersion(songId: String) = data.find { it.first == songId }?.second

    fun deleteSong(songId: String) {
        data.swap(data.filter { it.first == songId })
        async(CommonPool) {
            songDetailDatabase.songDetailDao().delete(songId)
        }
    }

    fun deleteAllSongs() {
        data.clear()
        async(CommonPool) {
            songDetailDatabase.songDetailDao().deleteAll()
        }
    }

    private fun refreshDataSet() {
        async(UI) {
            async(CommonPool) {
                songDetailDatabase.songDetailDao().getAllMetadata().map { Pair(it.id, it.version) }
            }.await().let {
                data.swap(it)
                isInitialized = true
            }
        }
    }

    interface Subscriber {

        fun onSuccess(songDetail: SongDetail)

        fun onError(song: Song)
    }
}