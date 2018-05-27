package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.home.shared.baseSongList.SongListAdapter
import com.pandulapeter.campfire.feature.home.shared.baseSongList.SongListItemViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject
import java.util.*

class PlaylistViewModel(
    context: Context,
    private val playlistId: String,
    private val openLibrary: () -> Unit,
    val toolbarTextInputView: ToolbarTextInputView?,
    private val onDataLoaded: () -> Unit
) : BaseSongListViewModel(context) {

    private val appShortcutManager by inject<AppShortcutManager>()
    private val analyticsManager by inject<AnalyticsManager>()
    private var songToDeleteId: String? = null
    val playlist = ObservableField<Playlist?>()
    val songCount = ObservableInt(-1)
    val isInEditMode = ObservableBoolean()
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST

    init {
        placeholderText.set(R.string.playlist_placeholder)
        buttonText.set(R.string.go_to_library)
        buttonIcon.set(R.drawable.ic_library_24dp)
        preferenceDatabase.lastScreen = playlistId
        toolbarTextInputView?.onDoneButtonPressed = {
            if (isInEditMode.get()) {
                toggleEditMode()
            }
        }
        isInEditMode.onPropertyChanged {
            if (adapter.itemCount > 1) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, SongListAdapter.Payload.EditModeChanged(it))
                updateAdapterItems()
            }
        }
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun Sequence<Song>.createViewModels(): List<SongListItemViewModel> {
        val list = (playlist.get()?.songIds ?: listOf<String>())
            .mapNotNull { songId -> find { it.id == songId } }
            .filter { it.id != songToDeleteId }
            .toList()
        return list.map {
            SongListItemViewModel.SongViewModel(
                context = context,
                songDetailRepository = songDetailRepository,
                playlistRepository = playlistRepository,
                song = it,
                shouldShowPlaylistButton = false,
                shouldShowDragHandle = isInEditMode.get() && list.size > 1
            )
        }
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        super.onPlaylistsUpdated(playlists)
        playlists.findLast { it.id == playlistId }.let {
            playlist.set(it)
            playlist.notifyChange()
            onDataLoaded()
        }
    }

    override fun onListUpdated(items: List<SongListItemViewModel>) {
        super.onListUpdated(items)
        songCount.set(items.size)
    }

    fun toggleEditMode() {
        toolbarTextInputView?.run {
            if (title.tag == null) {
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    (playlist.get()?.title ?: "").let {
                        textInput.setText(it)
                        textInput.setSelection(it.length)
                    }
                } else {
                    playlist.get()?.let {
                        val newTitle = textInput.text?.toString()
                        if (it.title != newTitle && newTitle != null && newTitle.trim().isNotEmpty()) {
                            playlistRepository.updatePlaylistTitle(it.id, newTitle)
                            appShortcutManager.updateAppShortcuts()
                        }
                        analyticsManager.onPlaylistEdited(newTitle ?: "", adapter.itemCount)
                    }
                }
                this@PlaylistViewModel.isInEditMode.set(toolbarTextInputView.isTextInputVisible)
            }
            return
        }
        isInEditMode.set(!isInEditMode.get())
    }

    fun restoreToolbarButtons() {
        playlist.get()?.let {
            onDataLoaded()
        }
    }

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
        playlist.get()?.let {
            adapter.notifyItemMoved(originalPosition, targetPosition)
            val newList = adapter.items.filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song.id }.toMutableList()
            it.songIds = newList
            playlistRepository.updatePlaylistSongIds(it.id, newList)
        }
    }

    fun hasSongToDelete() = songToDeleteId != null

    fun deleteSongTemporarily(songId: String) {
        analyticsManager.onSongPlaylistStateChanged(songId, playlistRepository.getPlaylistCountForSong(songId) - 1, AnalyticsManager.PARAM_VALUE_SWIPE_TO_DISMISS, false)
        songToDeleteId = songId
        updateAdapterItems()
    }

    fun cancelDeleteSong() {
        songToDeleteId?.let {
            analyticsManager.onSongPlaylistStateChanged(it, playlistRepository.getPlaylistCountForSong(it), AnalyticsManager.PARAM_VALUE_CANCEL_SWIPE_TO_DISMISS, false)
        }
        songToDeleteId = null
        updateAdapterItems()
    }

    fun deleteSongPermanently() {
        songToDeleteId?.let {
            playlist.get()?.let {
                val newList = adapter.items.filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song.id }.toMutableList()
                it.songIds = newList
                playlistRepository.updatePlaylistSongIds(it.id, newList)
            }
            songToDeleteId = null
        }
    }
}