package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel

class PlaylistViewModel(context: Context, private val playlistId: String, private val openLibrary: () -> Unit) : SongListViewModel(context) {

    override fun onActionButtonClicked() = openLibrary()

    override fun Sequence<Song>.createViewModels(): List<SongListItemViewModel> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}