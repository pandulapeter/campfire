package com.pandulapeter.campfire.feature.home.collections.detail

import android.content.Context
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel

class CollectionDetailViewModel(context: Context, private val collection: Collection) : SongListViewModel(context) {

    override fun onActionButtonClicked() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun Sequence<Song>.createViewModels(): List<SongListItemViewModel> {
        val list = (collection.songs ?: listOf())
            .mapNotNull { songId -> find { it.id == songId } }
            .toList()
        return list.map {
            SongListItemViewModel.SongViewModel(
                context = context,
                songDetailRepository = songDetailRepository,
                playlistRepository = playlistRepository,
                song = it,
                shouldShowPlaylistButton = false,
                shouldShowDragHandle = false
            )
        }
    }
}