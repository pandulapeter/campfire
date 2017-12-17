package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.Repository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.util.onPropertyChanged
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
    val editedTitle = ObservableField(title.get())
    val shouldShowPlayButton = ObservableBoolean()
    val isInEditMode = ObservableBoolean()
    val shouldAllowEditMode = playlistId != Playlist.FAVORITES_ID

    init {
        title.onPropertyChanged { editedTitle.set(it) }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        return playlistRepository.getPlaylistSongs(playlistId)
            .filterWorkInProgress()
            .filterExplicit()
            .map { songInfo ->
                SongInfoViewModel(
                    songInfo,
                    false,
                    true,
                    songInfoRepository.getDownloadedSongs().firstOrNull { songInfo.id == it.id }?.version ?: 0 != songInfo.version ?: 0)
            }
    }

    override fun onUpdate(updateType: Repository.UpdateType) {
        super.onUpdate(updateType)
        (playlistRepository.getPlaylist(playlistId) as? Playlist.Custom)?.let { title.set(it.title) }
        shouldShowPlayButton.set(adapter.items.isNotEmpty())
    }

    fun onDeleteButtonClicked() {
        //TODO
    }

    fun toggleEditMode() {
        if (shouldAllowEditMode) {
            if (isInEditMode.get()) {
                val newTitle = editedTitle.get()
                if (newTitle != null && newTitle.trim().isNotEmpty()) {
                    playlistRepository.updatePlaylistTitle(playlistId, newTitle.trim())
                }
            }
            isInEditMode.set(!isInEditMode.get())
        }
    }

    fun onPlayButtonClicked() {
        if (adapter.items.isNotEmpty()) {
            adapter.itemClickListener(0)
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