package com.pandulapeter.campfire.feature.home.playlist

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.onPropertyChanged
import java.util.Collections

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(songInfoRepository: SongInfoRepository,
                        downloadedSongRepository: DownloadedSongRepository,
                        appShortcutManager: AppShortcutManager,
                        private val playlistRepository: PlaylistRepository,
                        private val favoritesTitle: String,
                        private val playlistId: Int) : SongListViewModel(songInfoRepository, downloadedSongRepository) {
    var adapterItemCount = 0
    val title = ObservableField(favoritesTitle)
    val editedTitle = ObservableField(title.get())
    val shouldShowPlayButton = ObservableBoolean(playlistRepository.getPlaylistSongIds(playlistId).isNotEmpty())
    val isInEditMode = ObservableBoolean()
    val shouldShowDeleteConfirmation = ObservableBoolean()
    val shouldDisplayEditButton = ObservableBoolean()
    val shouldShowWorkInProgressSnackbar = ObservableBoolean()
    val shouldAllowDeleteButton = playlistId != Playlist.FAVORITES_ID

    init {
        title.onPropertyChanged { editedTitle.set(it) }
        isInEditMode.onPropertyChanged {
            onUpdate(UpdateType.EditModeChanged(playlistId, it))
        }
        appShortcutManager.onPlaylistOpened(playlistId)
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val items = playlistRepository.getPlaylistSongIds(playlistId)
            .mapNotNull { songInfoRepository.getSongInfo(it) }
        val shouldShowDragHandle = isInEditMode.get() && items.size > 1
        return items.map { songInfo ->
            val isDownloaded = downloadedSongRepository.isSongDownloaded(songInfo.id)
            val isSongNew = false //TODO: Check if the song is new.
            SongInfoViewModel(
                songInfo = songInfo,
                isSongDownloaded = isDownloaded,
                isSongLoading = downloadedSongRepository.isSongLoading(songInfo.id),
                isSongOnAnyPlaylist = false,
                shouldShowDragHandle = shouldShowDragHandle,
                shouldShowPlaylistButton = false,
                shouldShowDownloadButton = !isDownloaded || isSongNew,
                alertText = if (isDownloaded) {
                    if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                } else {
                    if (isSongNew) R.string.library_new else null
                })
        }
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.SongAddedToPlaylist -> if (updateType.playlistId == playlistId) super.onUpdate(updateType) //TODO: Call adapter.notifyItemAdded() instead.
            is UpdateType.SongRemovedFromPlaylist -> if (updateType.playlistId == playlistId) super.onUpdate(updateType) //TODO: Call adapter.notifyItemRemoved() instead.
            is UpdateType.PlaylistRenamed -> if (updateType.playlistId == playlistId) title.set(updateType.title)
            is UpdateType.PlaylistSongOrderUpdated -> if (updateType.playlistId == playlistId) super.onUpdate(updateType)
            is UpdateType.PlaylistsUpdated -> {
                super.onUpdate(updateType)
                playlistRepository.getPlaylist(playlistId)?.let { title.set(it.title ?: favoritesTitle) }
            }
            is UpdateType.SongAddedToDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.Payload.SONG_DOWNLOADED) }
            is UpdateType.SongRemovedFromDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.Payload.SONG_DOWNLOAD_DELETED) }
            is UpdateType.DownloadStarted -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.Payload.DOWNLOAD_STARTED) }
            is UpdateType.DownloadSuccessful -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.Payload.DOWNLOAD_SUCCESSFUL) }
            is UpdateType.DownloadFailed -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.Payload.DOWNLOAD_FAILED) }
            is UpdateType.EditModeChanged -> if (updateType.playlistId == playlistId) {
                val payload = if (updateType.isInEditMode && adapterItemCount > 1) SongInfoAdapter.Payload.EDIT_MODE_OPEN else SongInfoAdapter.Payload.EDIT_MODE_CLOSE
                adapter.items.forEachIndexed { index, _ -> adapter.notifyItemChanged(index, payload) }
                shouldShowPlayButton.set(if (!updateType.isInEditMode) adapterItemCount > 0 else false)
            }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        adapterItemCount = items.size
        shouldDisplayEditButton.set(adapterItemCount > 0 || playlistId != Playlist.FAVORITES_ID)
        if (!isInEditMode.get()) {
            shouldShowPlayButton.set(adapterItemCount > 0)
        }
    }

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
            isInEditMode.set(false)
        } else {
            isInEditMode.set(true)
        }
    }

    fun onPlayButtonClicked() {
        if (adapterItemCount > 0) adapter.itemClickListener(0)
    }

    fun onShareButtonClicked() {
        //TODO: Implement deep link sharing.
        shouldShowWorkInProgressSnackbar.set(true)
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