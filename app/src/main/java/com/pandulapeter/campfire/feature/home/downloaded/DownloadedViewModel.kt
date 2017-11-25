package com.pandulapeter.campfire.feature.home.downloaded

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel

/**
 * Handles events and logic for [DownloadedFragment].
 */
class DownloadedViewModel(private val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()
    val isLoading = ObservableBoolean(false)

    init {
        refreshAdapterItems(songInfoRepository.getDownloaded())
    }

    fun addOrRemoveSongFromFavorites(songInfo: SongInfo) {
        if (songInfoRepository.getFavorites().contains(songInfo.id)) {
            songInfoRepository.removeSongFromFavorites(songInfo)
        } else {
            songInfoRepository.addSongToFavorites(songInfo)
        }
        refreshAdapterItems(adapter.items.map { it.songInfo })
    }

    private fun refreshAdapterItems(newData: List<SongInfo>) {
        val favorites = songInfoRepository.getFavorites()
        adapter.items = songInfoRepository.getDownloaded().map { SongInfoViewModel(it, favorites.contains(it.id), true) }
    }
}