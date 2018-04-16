package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel

class PlaylistViewModel(
    context: Context,
    private val playlistId: String,
    private val openLibrary: () -> Unit
) : SongListViewModel(context) {

    var playlist = ObservableField<Playlist?>()
    private var songToDeleteId: String? = null
    val songCount = ObservableInt()

    init {
        placeholderText.set(R.string.playlist_placeholder)
        buttonText.set(R.string.go_to_library)
        preferenceDatabase.lastScreen = playlistId
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun Sequence<Song>.createViewModels() = filter { it.id != songToDeleteId }
        .filter { playlist.get()?.songIds?.contains(it.id) ?: false }
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
        playlist.set(playlists.findLast { it.id == playlistId })
    }

    override fun onListUpdated(items: List<SongListItemViewModel>) {
        super.onListUpdated(items)
        songCount.set(items.size)
    }
}