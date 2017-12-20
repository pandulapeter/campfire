package com.pandulapeter.campfire.feature.home.playlist

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.util.onPropertyChanged
import java.util.Collections

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    private val favoritesTitle: String,
    private val playlistId: Int) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val title = ObservableField(favoritesTitle)
    val editedTitle = ObservableField(title.get())
    val shouldShowPlayButton = ObservableBoolean()
    val isInEditMode = ObservableBoolean()
    val shouldShowDeleteConfirmation = ObservableBoolean()
    val shouldDisplayEditButton = ObservableBoolean()
    val shouldAllowDeleteButton = playlistId != Playlist.FAVORITES_ID

    init {
        title.onPropertyChanged { editedTitle.set(it) }
        isInEditMode.onPropertyChanged {
            shouldShowPlayButton.set(if (it) false else isAdapterNotEmpty)
            onUpdate(UpdateType.Unspecified)
        }
    }

    override fun getAdapterItems() = playlistRepository.getPlaylistSongIds(playlistId)
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .filterWorkInProgress()
        .filterExplicit()

    override fun onUpdate(updateType: UpdateType) {
        super.onUpdate(updateType)
        shouldDisplayEditButton.set(isAdapterNotEmpty || playlistId != Playlist.FAVORITES_ID)
        playlistRepository.getPlaylist(playlistId)?.let { title.set(it.title ?: favoritesTitle) }
        if (!isInEditMode.get()) {
            shouldShowPlayButton.set(isAdapterNotEmpty)
        }
    }

    override fun shouldShowDragHandle(itemCount: Int) = isInEditMode.get() && itemCount > 1

    override fun shouldShowPlaylistButton() = false

    fun onDeleteButtonClicked() {
        if (shouldAllowDeleteButton) {
            shouldShowDeleteConfirmation.set(true)
        }
    }

    fun deletePlaylist() {
        playlistRepository.unsubscribe(this)
        playlistRepository.deletePlaylist(playlistId)
    }

    fun toggleEditMode() {
        if (isInEditMode.get()) {
            val newTitle = editedTitle.get()
            if (newTitle != null && newTitle.trim().isNotEmpty()) {
                playlistRepository.renamePlaylist(playlistId, newTitle.trim())
            }
        }
        isInEditMode.set(!isInEditMode.get())
    }

    fun onPlayButtonClicked() {
        if (isAdapterNotEmpty) {
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
        playlistRepository.updatePlaylist(playlistId, list)
    }
}