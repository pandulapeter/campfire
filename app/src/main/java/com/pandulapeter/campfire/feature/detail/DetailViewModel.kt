package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [DetailFragment].
 */
class DetailViewModel(fragmentManager: FragmentManager,
                      songId: String,
                      playlistId: Int,
                      playlistRepository: PlaylistRepository,
                      private val songInfoRepository: SongInfoRepository,
                      private val historyRepository: HistoryRepository) : CampfireViewModel() {
    val title = ObservableField("")
    val artist = ObservableField("")
    val songIds = playlistRepository.getPlaylist(playlistId)?.songIds ?: listOf(songId)
    val adapter = SongPagerAdapter(fragmentManager, songIds)

    init {
        updateToolbar(songIds[0])
    }

    fun onPageSelected(position: Int) {
        songIds[position].let {
            updateToolbar(it)
            historyRepository.addToHistory(it)
        }
    }

    private fun updateToolbar(songId: String) {
        songInfoRepository.getSongInfo(songId)?.let {
            title.set(it.title)
            artist.set(it.artist)
        }
    }
}