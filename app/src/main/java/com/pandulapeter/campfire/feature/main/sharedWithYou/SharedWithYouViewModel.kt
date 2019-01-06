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
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.mutableLiveDataOf
import com.pandulapeter.campfire.util.removePrefixes

class SharedWithYouViewModel(
    private val songIds: List<String>,
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : BaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager, interactionBlocker) {

    val songCount = mutableLiveDataOf(0)
    val shouldOpenSongs = MutableLiveData<Boolean?>()
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_SHARED_WITH_YOU
    override val placeholderText = R.string.shared_with_you_empty_list
    override val buttonIcon = R.drawable.ic_songs

    init {
        buttonText.value = R.string.go_to_songs
    }

    override fun Sequence<Song>.createViewModels() = songIds
        .mapNotNull { songId -> find { it.id == songId } }
        .sortedBy { it.getNormalizedTitle().removePrefixes() }
        .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
        .toList()

    override fun onListUpdated(items: List<ItemViewModel>) {
        super.onListUpdated(items)
        songCount.value = items.size
    }

    override fun onActionButtonClicked() {
        shouldOpenSongs.value = true
    }
}