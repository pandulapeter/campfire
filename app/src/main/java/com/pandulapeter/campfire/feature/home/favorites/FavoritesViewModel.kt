package com.pandulapeter.campfire.feature.home.favorites

import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.sort

/**
 * Handles events and logic for [FavoritesFragment].
 */
class FavoritesViewModel(private val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()

    init {
        refreshAdapterItems()
    }

    fun addSongToFavorites(songInfo: SongInfo) {
        songInfoRepository.addSongToFavorites(songInfo)
        refreshAdapterItems()
    }

    fun removeSongFromFavorites(songInfo: SongInfo) {
        songInfoRepository.removeSongFromFavorites(songInfo)
        refreshAdapterItems()
    }

    private fun refreshAdapterItems() {
        val favorites = songInfoRepository.getFavorites()
        adapter.items = songInfoRepository.getDownloaded().sort().filter { favorites.contains(it.id) }.map { SongInfoViewModel(it, false, false, false) }
    }
}