package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import java.util.Collections

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    context: Context?,
    private val playlistRepository: PlaylistRepository,
    private val playlistId: Int) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository) {
    val title = ObservableField(context?.getString(R.string.home_favorites))

    init {
        (playlistRepository.getPlaylist(playlistId) as? Playlist.Custom)?.let { title.set(it.name) }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        return playlistRepository.getPlaylistSongs(playlistId)
            .filterWorkInProgress()
            .filterExplicit()
            .map { songInfo ->
                SongInfoViewModel(
                    songInfo,
                    true,
                    songInfoRepository.getDownloadedSongs().firstOrNull { songInfo.id == it.id }?.version ?: 0 != songInfo.version ?: 0)
            }
    }

    fun removeSongFromPlaylist(songId: String) = playlistRepository.removeSongFromPlaylist(playlistId, songId)

    fun swapSongsInPlaylist(originalPosition: Int, targetPosition: Int) {
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
        playlistRepository.setPlaylist(playlistId, list)
    }
}