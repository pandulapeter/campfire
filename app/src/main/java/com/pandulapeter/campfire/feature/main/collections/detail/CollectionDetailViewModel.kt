package com.pandulapeter.campfire.feature.main.collections.detail

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import org.koin.android.ext.android.inject

class CollectionDetailViewModel(
    context: Context,
    collection: Collection,
    private val onDataLoaded: () -> Unit
) : BaseSongListViewModel(context) {

    val collectionRepository by inject<CollectionRepository>()
    override val cardTransitionName = "card-${collection.id}"
    override val imageTransitionName = "image-${collection.id}"
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL

    init {
        this.collection.set(CollectionItemViewModel(collection, context.getString(R.string.new_tag)))
    }

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        super.onSongRepositoryDataUpdated(data)
        if (data.isNotEmpty()) {
            onDataLoaded()
        }
    }

    override fun onActionButtonClicked() = updateData()

    override fun Sequence<Song>.createViewModels() = (collection.get()?.collection?.songs ?: listOf())
        .mapNotNull { songId -> find { it.id == songId } }
        .map {
            SongItemViewModel(
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