package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.songList.SongListAdapter
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.onPropertyChanged
import java.util.*

class PlaylistViewModel(
    context: Context,
    private val playlistId: String,
    private val openLibrary: () -> Unit,
    val toolbarTextInputView: ToolbarTextInputView?,
    private val onDataLoaded: (Boolean) -> Unit
) : SongListViewModel(context) {

    var playlist = ObservableField<Playlist?>()
    private var songToDeleteId: String? = null
    val songCount = ObservableInt()
    val isInEditMode = ObservableBoolean()

    init {
        placeholderText.set(R.string.playlist_placeholder)
        buttonText.set(R.string.go_to_library)
        preferenceDatabase.lastScreen = playlistId
        isInEditMode.onPropertyChanged {
            if (adapter.items.size > 1) {
                adapter.items.forEachIndexed { index, item ->
                    if (item is SongListItemViewModel.SongViewModel) {
                        adapter.notifyItemChanged(index, SongListAdapter.Payload.EditModeChanged(it))
                    }
                }
            }
        }
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun Sequence<Song>.createViewModels() = (playlist.get()?.songIds ?: listOf<String>())
        .mapNotNull { songId -> find { it.id == songId } }
        .filter { it.id != songToDeleteId && playlist.get()?.songIds?.contains(it.id) ?: false }
        .map {
            SongListItemViewModel.SongViewModel(
                context = context,
                songDetailRepository = songDetailRepository,
                playlistRepository = playlistRepository,
                song = it,
                shouldShowPlaylistButton = false
            )
        }
        .toList()

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        super.onPlaylistsUpdated(playlists)
        playlists.findLast { it.id == playlistId }.let {
            playlist.set(it)
            onDataLoaded(playlistId != Playlist.FAVORITES_ID || (it?.songIds?.size ?: 0) > 1)
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
                    textInput.setText("")
                }
                this@PlaylistViewModel.isInEditMode.set(toolbarTextInputView.isTextInputVisible)
            }
            return
        }
        isInEditMode.set(!isInEditMode.get())
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
}