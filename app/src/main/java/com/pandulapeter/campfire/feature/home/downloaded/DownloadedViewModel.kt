package com.pandulapeter.campfire.feature.home.downloaded

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.sort

/**
 * Handles events and logic for [DownloadedFragment].
 */
class DownloadedViewModel(private val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()
    val isLoading = ObservableBoolean(false)

    init {
        refreshAdapterItems()
    }

    fun addOrRemoveSongFromFavorites(songInfo: SongInfo) {
        if (songInfoRepository.getFavorites().contains(songInfo.id)) {
            songInfoRepository.removeSongFromFavorites(songInfo)
        } else {
            songInfoRepository.addSongToFavorites(songInfo)
        }
        refreshAdapterItems()
    }

    fun addSongToDownloaded(songInfo: SongInfo) {
        songInfoRepository.addSongToDownloaded(songInfo)
        refreshAdapterItems()
    }

    fun removeSongFromDownloaded(songInfo: SongInfo) {
        songInfoRepository.removeSongFromDownloaded(songInfo)
        refreshAdapterItems()
    }

    private fun refreshAdapterItems() {
        val favorites = songInfoRepository.getFavorites()
        adapter.items = songInfoRepository.getDownloaded().sort().map { SongInfoViewModel(it, favorites.contains(it.id), true) }
    }
}