package com.pandulapeter.campfire.feature.home.downloaded

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.sort

/**
 * Handles events and logic for [DownloadedFragment].
 */
class DownloadedViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {

    init {
        refreshAdapterItems()
    }

    fun addOrRemoveSongFromFavorites(songInfo: SongInfo) {
        if (songInfoRepository.getFavoriteIds().contains(songInfo.id)) {
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
        val favorites = songInfoRepository.getFavoriteIds()
        adapter.items = songInfoRepository.getDownloadedSongs().sort().map { songInfo ->
            val isTinted = favorites.contains(songInfo.id)
            SongInfoViewModel(
                songInfo = songInfo,
                actionDescription = if (isTinted) R.string.downloaded_remove_from_favorites else R.string.downloaded_add_to_favorites,
                actionIcon = R.drawable.ic_favorites_24dp,
                isActionTinted = isTinted)
        }
    }
}