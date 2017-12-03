package com.pandulapeter.campfire.feature.home.playlist

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import java.util.Collections

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    songInfoRepository: SongInfoRepository,
    playlistRepository: PlaylistRepository) : SongListViewModel(homeCallbacks, songInfoRepository, playlistRepository) {
    val shouldShowShuffle = ObservableBoolean(false)

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongs = songInfoRepository.getDownloadedSongs()
        val downloadedSongIds = downloadedSongs.map { it.id }
        return playlistRepository.getFavoriteSongs().map { songInfo ->
            SongInfoViewModel(
                songInfo,
                true,
                downloadedSongs.firstOrNull { songInfo.id == it.id }?.version ?: 0 != songInfo.version ?: 0)
        }
    }

    override fun onUpdate() {
        super.onUpdate()
        shouldShowShuffle.set(adapter.itemCount > SongInfoRepository.SHUFFLE_LIMIT)
    }

    fun removeSongFromFavorites(id: String) = playlistRepository.removeSongFromFavorites(id)

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
        playlistRepository.setFavorites(list)
    }

    fun shuffleItems() = playlistRepository.shuffleFavorites()
}