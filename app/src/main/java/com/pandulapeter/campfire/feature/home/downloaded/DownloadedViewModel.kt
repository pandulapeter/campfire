package com.pandulapeter.campfire.feature.home.downloaded

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.sort

/**
 * Handles events and logic for [DownloadedFragment].
 */
class DownloadedViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {

    override fun getAdapterItems() = songInfoRepository.getDownloadedSongs().sort().map { songInfo ->
        val isTinted = songInfoRepository.isSongFavorite(songInfo.id)
        SongInfoViewModel(
            songInfo = songInfo,
            actionDescription = if (isTinted) R.string.downloaded_remove_from_favorites else R.string.downloaded_add_to_favorites,
            actionIcon = R.drawable.ic_favorites_24dp,
            isActionTinted = isTinted)
    }
}