package com.pandulapeter.campfire.feature.home.collections.detail

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.home.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import org.koin.android.ext.android.inject

class CollectionDetailViewModel(
    context: Context,
    collection: Collection,
    private val onDataLoaded: () -> Unit
) : SongListViewModel(context) {

    val collectionRepository by inject<CollectionRepository>()
    override val cardTransitionName = "card-${collection.id}"
    override val imageTransitionName = "image-${collection.id}"
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL

    init {
        this.collection.set(CollectionListItemViewModel.CollectionViewModel(collection, context.getString(R.string.new_tag)))
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