package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.library.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [DetailFragment].
 */
class DetailViewModel(songId: String,
                      playlistId: Int,
                      analyticsManager: AnalyticsManager,
                      userPreferenceRepository: UserPreferenceRepository,
                      private val fragmentManager: FragmentManager,
                      private val playlistRepository: PlaylistRepository,
                      private val songInfoRepository: SongInfoRepository,
                      private val historyRepository: HistoryRepository) : CampfireViewModel(analyticsManager), Subscriber {
    val title = ObservableField("")
    val artist = ObservableField("")
    val songIds = playlistRepository.getPlaylist(playlistId)?.songIds ?: listOf(songId)
    val adapter = SongPagerAdapter(fragmentManager, songIds)
    val shouldNavigateBack = ObservableBoolean()
    val isSongOnAnyPlaylist = ObservableBoolean()
    val shouldShowSongOptions = ObservableBoolean()
    val shouldShowPlaylistAction = playlistId == DetailFragment.NO_PLAYLIST
    val youTubeSearchQuery = ObservableField<String>()
    val shouldShowChords = ObservableBoolean(userPreferenceRepository.shouldShowChords)
    val shouldAllowToolbarScrolling = ObservableBoolean() //TODO: Enable this when the loading is successful.
    private var selectedPosition = 0

    init {
        shouldShowChords.onPropertyChanged { userPreferenceRepository.shouldShowChords = it }
        updateToolbar(songIds[selectedPosition])
    }

    override fun onUpdate(updateType: UpdateType) {
        if (updateType is UpdateType.PlaylistsUpdated
            || updateType is UpdateType.SongRemovedFromPlaylist && updateType.songId == songIds[selectedPosition]
            || updateType is UpdateType.SongAddedToPlaylist && updateType.songId == songIds[selectedPosition]) {
            updatePlaylistActionIcon()
        }
    }

    fun onPageSelected(position: Int) {
        selectedPosition = position
        songIds[position].let {
            updateToolbar(it)
            historyRepository.addToHistory(it)
        }
    }

    fun onPlaylistActionClicked() {
        val songId = songIds[selectedPosition]
        if (playlistRepository.getPlaylists().size == 1) {
            if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
                playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
            } else {
                playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
            }
        } else {
            PlaylistChooserBottomSheetFragment.show(fragmentManager, songId)
        }
    }

    fun navigateBack() = shouldNavigateBack.set(true)

    fun showSongOptions() = shouldShowSongOptions.set(true)

    fun onPlayOnYouTubeClicked() {
        songInfoRepository.getSongInfo(songIds[selectedPosition])?.let {
            youTubeSearchQuery.set("${it.artist} - ${it.title}")
        }
    }

    private fun updateToolbar(songId: String) {
        songInfoRepository.getSongInfo(songId)?.let {
            title.set(it.title)
            artist.set(it.artist)
            updatePlaylistActionIcon()
        }
    }

    private fun updatePlaylistActionIcon() {
        isSongOnAnyPlaylist.set(playlistRepository.isSongInAnyPlaylist(songIds[selectedPosition]))
    }
}