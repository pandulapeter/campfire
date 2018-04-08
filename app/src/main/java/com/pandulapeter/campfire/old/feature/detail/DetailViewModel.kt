package com.pandulapeter.campfire.old.feature.detail

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.SongInfoRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.old.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [DetailFragment].
 */
class DetailViewModel(
    songId: String,
    playlistId: Int,
    analyticsManager: AnalyticsManager,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val downloadedSongRepository: DownloadedSongRepository,
    private val fragmentManager: FragmentManager,
    private val playlistRepository: PlaylistRepository,
    private val songInfoRepository: SongInfoRepository
) : CampfireViewModel(analyticsManager), Subscriber {
    val title = ObservableField("")
    val artist = ObservableField("")
    val songIds = playlistRepository.getPlaylist(playlistId)?.songIds ?: listOf(songId)
    val adapter = SongPagerAdapter(fragmentManager, songIds)
    private val shouldNavigateBack = ObservableBoolean()
    val isSongOnAnyPlaylist = ObservableBoolean()
    val shouldShowSongOptions = ObservableBoolean()
    val shouldShowPlaylistAction = playlistId == DetailFragment.NO_PLAYLIST
    val playOriginalSearchQuery = ObservableField<String>()
    val shouldShowAutoScrollButton = ObservableBoolean()
    val isAutoScrollStarted = ObservableBoolean()
    val isAutoScrollEnabled = userPreferenceRepository.shouldEnableAutoScroll
    val autoScrollSpeed = ObservableInt()
    val transposition = ObservableInt()
    private var selectedPosition = songIds.indexOf(songId)

    init {
        onSongSelected()
        autoScrollSpeed.onPropertyChanged { userPreferenceRepository.setSongAutoScrollSpeed(getSelectedSongId(), it) }
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.PlaylistsUpdated -> updatePlaylistActionIcon()
            is UpdateType.SongRemovedFromPlaylist -> if (updateType.songId == getSelectedSongId()) updatePlaylistActionIcon()
            is UpdateType.SongAddedToPlaylist -> if (updateType.songId == getSelectedSongId()) updatePlaylistActionIcon()
            is UpdateType.Download.Started -> if (updateType.songId == getSelectedSongId()) {
                shouldShowAutoScrollButton.set(false)
            }
            is UpdateType.Download.Successful -> if (updateType.songId == getSelectedSongId()) {
                shouldShowAutoScrollButton.set(true)
            }
            is UpdateType.SongTransposed -> if (updateType.songId == getSelectedSongId()) {
                transposition.set(updateType.transposedVaue)
            }
            is UpdateType.ContentEndReached -> if (updateType.songId == getSelectedSongId()) {
                isAutoScrollStarted.set(false)
            }
        }
    }

    fun onPageSelected(position: Int) {
        selectedPosition = position
        onSongSelected()
        shouldShowAutoScrollButton.set(downloadedSongRepository.isSongDownloaded(getSelectedSongId()))
    }

    fun onPlaylistActionClicked() {
        val songId = getSelectedSongId()
        if (playlistRepository.getPlaylists().size == 1) {
            if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
                playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
            } else {
                playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
            }
        } else {
            isAutoScrollStarted.set(false)
            PlaylistChooserBottomSheetFragment.show(fragmentManager, songId)
        }
    }

    fun navigateBack() {
        isAutoScrollStarted.set(false)
        shouldShowAutoScrollButton.set(false)
        shouldNavigateBack.set(true)
    }

    fun showSongOptions() = shouldShowSongOptions.set(true)

    fun onPlayOnYouTubeClicked() {
        songInfoRepository.getSongInfo(getSelectedSongId())?.let {
            playOriginalSearchQuery.set("${it.artist} - ${it.title}")
        }
    }

    //TODO: Users should not be able to interrupt the animation.
    fun onAutoPlayButtonClicked() = isAutoScrollStarted.set(!isAutoScrollStarted.get())

    fun getSelectedSongId() = songIds[selectedPosition]

    private fun onSongSelected() {
        songInfoRepository.getSongInfo(getSelectedSongId())?.let {
            title.set(it.title)
            artist.set(it.artist)
            updatePlaylistActionIcon()
            transposition.set(userPreferenceRepository.getSongTransposition(it.id))
            autoScrollSpeed.set(userPreferenceRepository.getSongAutoScrollSpeed(it.id))
        }
    }

    private fun updatePlaylistActionIcon() {
        isSongOnAnyPlaylist.set(playlistRepository.isSongInAnyPlaylist(getSelectedSongId()))
    }
}