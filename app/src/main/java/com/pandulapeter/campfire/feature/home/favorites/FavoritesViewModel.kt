package com.pandulapeter.campfire.feature.home.favorites

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel

/**
 * Handles events and logic for [FavoritesFragment].
 */
class FavoritesViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {
    val shouldShowShuffle = ObservableBoolean(false)

    override fun getAdapterItems() = songInfoRepository.getFavoriteSongs().map { songInfo ->
        SongInfoViewModel(
            songInfo = songInfo,
            actionDescription = R.string.favorites_drag_item_to_rearrange,
            actionIcon = R.drawable.ic_drag_handle_24dp,
            isActionTinted = false)
    }

    override fun onUpdate() {
        super.onUpdate()
        shouldShowShuffle.set(adapter.itemCount > SongInfoRepository.SHUFFLE_LIMIT)
    }

    fun removeSongFromFavorites(id: String) = songInfoRepository.removeSongFromFavorites(id)

    fun swapSongsInFavorites(originalPosition: Int, targetPosition: Int) = songInfoRepository.swapSongFavoritesPositions(originalPosition, targetPosition)

    fun shuffleItems() = songInfoRepository.shuffleFavorites()
}