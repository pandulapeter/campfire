package com.pandulapeter.campfire.old.feature.home.playlist

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.SongInfoRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.old.integration.AppShortcutManager
import com.pandulapeter.campfire.old.integration.DeepLinkManager
import com.pandulapeter.campfire.old.networking.AnalyticsManager
import com.pandulapeter.campfire.old.util.onPropertyChanged
import java.util.*

/**
 * Handles events and logic for [PlaylistFragment].
 */
class PlaylistViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    deepLinkManager: DeepLinkManager,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    appShortcutManager: AppShortcutManager,
    playlistRepository: PlaylistRepository,
    userPreferenceRepository: UserPreferenceRepository,
    private val favoritesTitle: String,
    private val playlistId: Int
) : SongInfoListViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository, userPreferenceRepository) {
    val title = ObservableField(favoritesTitle)
    val songCount = ObservableInt(playlistRepository.getPlaylistSongIds(playlistId).size)
    val editedTitle = ObservableField(title.get())
    val shouldShowShareButton = ObservableBoolean(songCount.get() > 0)
    val isInEditMode = ObservableBoolean()
    val shouldShowEditButton = ObservableBoolean(shouldShowShareButton.get() || playlistId != Playlist.FAVORITES_ID)
    val shouldShowWorkInProgressSnackbar = ObservableBoolean()
    val isCustomPlaylist = playlistId != Playlist.FAVORITES_ID

    init {
        title.onPropertyChanged { editedTitle.set(it) }
        isInEditMode.onPropertyChanged { onUpdate(UpdateType.EditModeChanged(playlistId, it)) }
        appShortcutManager.onPlaylistOpened(playlistId)
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val items = playlistRepository.getPlaylistSongIds(playlistId)
            .asSequence()
            .mapNotNull { songInfoRepository.getSongInfo(it) }
        val shouldShowDragHandle = isInEditMode.get() && items.toList().size > 1
        return items.map { songInfo ->
            SongInfoViewModel(
                songInfo = songInfo,
                downloadState = downloadedSongRepository.getSongDownloadedState(songInfo.id),
                isSongOnAnyPlaylist = false,
                shouldShowDragHandle = shouldShowDragHandle,
                shouldShowPlaylistButton = false,
                updateText = updateString,
                newText = newString
            )
        }.toList()
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.SongAddedToPlaylist -> if (updateType.playlistId == playlistId) super.onUpdate(updateType)
            is UpdateType.SongRemovedFromPlaylist -> if (updateType.playlistId == playlistId) super.onUpdate(updateType)
            is UpdateType.PlaylistRenamed -> if (updateType.playlistId == playlistId) title.set(updateType.title)
            is UpdateType.PlaylistsUpdated -> {
                super.onUpdate(updateType)
                playlistRepository.getPlaylist(playlistId)?.let { title.set(it.title ?: favoritesTitle) }
            }
            is UpdateType.Download -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DownloadStateChanged(downloadedSongRepository.getSongDownloadedState(updateType.songId)))
            }
            is UpdateType.EditModeChanged -> if (updateType.playlistId == playlistId) {
                val payload = SongInfoListAdapter.Payload.EditModeChanged(updateType.isInEditMode && adapter.items.size > 1)
                if (playlistId == Playlist.FAVORITES_ID) {
                    title.notifyChange() // Needed to fix a visual glitch.
                }
                adapter.items.forEachIndexed { index, _ -> adapter.notifyItemChanged(index, payload) }
            }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        shouldShowEditButton.set(items.isNotEmpty() || playlistId != Playlist.FAVORITES_ID)
        shouldShowShareButton.set(items.isNotEmpty())
        songCount.set(items.size)
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

    fun onShareButtonClicked() {
        //TODO: Implement deep link sharing.
        shouldShowWorkInProgressSnackbar.set(true)
    }

    fun removeSongFromPlaylist(songId: String) = playlistRepository.removeSongFromPlaylist(playlistId, songId)

    fun swapSongsInPlaylist(originalPosition: Int, targetPosition: Int) {
        if (originalPosition < targetPosition) {
            for (i in originalPosition until targetPosition) {
                Collections.swap(adapter.items, i, i + 1)
            }
        } else {
            for (i in originalPosition downTo targetPosition + 1) {
                Collections.swap(adapter.items, i, i - 1)
            }
        }
        adapter.notifyItemMoved(originalPosition, targetPosition)
        playlistRepository.updatePlaylist(playlistId, adapter.items.map { it.songInfo.id }.toMutableList())
    }
}