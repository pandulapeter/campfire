package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel

class PlaylistViewModel(
    context: Context,
    private val playlistId: String,
    private val openLibrary: () -> Unit,
    private val updateToolbar: (title: String) -> Unit
) : SongListViewModel(context) {

    private var playlist: Playlist? = null
    private var songToDeleteId: String? = null

    init {
        placeholderText.set(R.string.playlist_placeholder)
        buttonText.set(R.string.go_to_library)
        preferenceDatabase.lastScreen = playlistId
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .filter { playlist?.songIds?.contains(it.id) ?: false }
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
        playlist = playlists.findLast { it.id == playlistId }
        updateToolbar(playlist?.title ?: context.getString(R.string.home_favorites))
    }
}