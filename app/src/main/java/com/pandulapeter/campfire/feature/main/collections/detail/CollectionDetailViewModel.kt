package com.pandulapeter.campfire.feature.main.collections.detail

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.main.shared.baseSongList.OldBaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager

class CollectionDetailViewModel(
    collection: Collection,
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    val collectionRepository: CollectionRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    private val onDataLoaded: () -> Unit //TODO: Replace with LiveData
) : OldBaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager) {

    override val cardTransitionName = "card-${collection.id}"
    override val imageTransitionName = "image-${collection.id}"
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL

    init {
        collectionRepository.onCollectionOpened(collection.id)
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