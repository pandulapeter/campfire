package com.pandulapeter.campfire.feature.main.sharedWithYou

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.mutableLiveDataOf
import com.pandulapeter.campfire.util.removePrefixes

class SharedWithYouViewModel(
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : BaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager, interactionBlocker) {

    var songIds = listOf<String>()
        set(value) {
            field = value
            updateAdapterItems(true)
        }
    val songCount = mutableLiveDataOf(0)
    val shouldTryAgain = MutableLiveData<Boolean?>()
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_SHARED_WITH_YOU
    override val placeholderText = R.string.something_went_wrong

    init {
        buttonText.value = R.string.try_again
    }

    override fun Sequence<Song>.createViewModels() = songIds
        .mapNotNull { songId -> find { it.id == songId } }
        .sortedBy { it.getNormalizedTitle().removePrefixes() }
        .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
        .toList()

    override fun onListUpdated(items: List<ItemViewModel>) {
        if (songIds.isNotEmpty()) {
            state.value = if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL
        }
        songCount.value = items.size
    }

    override fun onActionButtonClicked() {
        shouldTryAgain.value = true
    }
}