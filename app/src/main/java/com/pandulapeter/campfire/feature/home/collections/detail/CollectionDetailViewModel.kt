package com.pandulapeter.campfire.feature.home.collections.detail

import android.content.Context
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel

class CollectionDetailViewModel(
    context: Context,
    collection: Collection,
    private val onDataLoaded: () -> Unit
) : SongListViewModel(context) {

    init {
        this.collection.set(CollectionListItemViewModel.CollectionViewModel(collection))
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        super.onSongRepositoryDataUpdated(data)
        if (data.isNotEmpty()) {
            onDataLoaded()
        }
    }

    override fun onActionButtonClicked() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun Sequence<Song>.createViewModels() = (collection.get()?.collection?.songs ?: listOf())
        .mapNotNull { songId -> find { it.id == songId } }
        .map {
            SongListItemViewModel.SongViewModel(
                context = context,
                songDetailRepository = songDetailRepository,
                playlistRepository = playlistRepository,
                song = it
            )
        }

    fun restoreToolbarButtons() {
        if (adapter.items.isNotEmpty()) {
            onDataLoaded()
        }
    }
}