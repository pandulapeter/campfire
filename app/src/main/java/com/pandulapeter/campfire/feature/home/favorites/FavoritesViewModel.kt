package com.pandulapeter.campfire.feature.home.favorites

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel

/**
 * Handles events and logic for [FavoritesFragment].
 */
class FavoritesViewModel(private val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()

    init {
        refreshAdapterItems()
    }

    fun addSongToFavorites(songInfo: SongInfo, position: Int) {
        songInfoRepository.addSongToFavorites(songInfo, position)
        refreshAdapterItems()
    }

    fun removeSongFromFavorites(songInfo: SongInfo) {
        songInfoRepository.removeSongFromFavorites(songInfo)
        refreshAdapterItems()
    }

    private fun refreshAdapterItems() {
        val downloaded = songInfoRepository.getDownloaded()
        adapter.items = songInfoRepository.getFavorites().mapNotNull { id ->
            downloaded.find { it.id == id }?.let { songInfo ->
                SongInfoViewModel(songInfo, false, false, false)
            }
        }
    }
}