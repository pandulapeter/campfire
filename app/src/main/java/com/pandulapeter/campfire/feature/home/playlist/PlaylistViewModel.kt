package com.pandulapeter.campfire.feature.home.playlist

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import java.util.Collections

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : SongListViewModel(homeCallbacks, songInfoRepository) {
    val shouldShowShuffle = ObservableBoolean(false)

    override fun onUpdate() {
        adapter.items = songInfoRepository.getFavoriteSongs().map { songInfo ->
            SongInfoViewModel(
                songInfo = songInfo,
                actionIcon = R.drawable.ic_drag_handle_24dp,
                isActionTinted = false)
        }
        shouldShowShuffle.set(adapter.itemCount > SongInfoRepository.SHUFFLE_LIMIT)
    }

    fun removeSongFromFavorites(id: String) = songInfoRepository.removeSongFromFavorites(id)

    fun swapSongsInFavorites(originalPosition: Int, targetPosition: Int) {
        val list = adapter.items.map { it.songInfo.id }.toMutableList()
        if (originalPosition < targetPosition) {
            for (i in originalPosition until targetPosition) {
                Collections.swap(list, i, i + 1)
            }
        } else {
            for (i in originalPosition downTo targetPosition + 1) {
                Collections.swap(list, i, i - 1)
            }
        }
        songInfoRepository.setFavorites(list)
    }

    fun shuffleItems() = songInfoRepository.shuffleFavorites()
}