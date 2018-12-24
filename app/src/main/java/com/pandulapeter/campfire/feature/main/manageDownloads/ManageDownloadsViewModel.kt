package com.pandulapeter.campfire.feature.main.manageDownloads

import android.content.Context
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.baseSongList.OldBaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.removePrefixes

class ManageDownloadsViewModel(
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    private val openSongs: () -> Unit //TODO: Replace with LiveData
) : OldBaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager) {

    val shouldShowDeleteAll = ObservableBoolean()
    val songCount = ObservableInt()
    private var songToDeleteId: String? = null
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS
    override val placeholderText = R.string.manage_downloads_placeholder
    override val buttonIcon = R.drawable.ic_songs_24dp

    init {
        buttonText.value = R.string.go_to_songs
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_MANAGE_DOWNLOADS
    }

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .sortedBy { it.getNormalizedTitle().removePrefixes() }
        .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
        .toList()

    override fun onListUpdated(items: List<ItemViewModel>) {
        super.onListUpdated(items)
        songCount.set(items.size)
        shouldShowDeleteAll.set(items.isNotEmpty())
    }

    override fun onActionButtonClicked() = openSongs()

    fun deleteAllSongs() = songDetailRepository.deleteAllSongs()

    fun deleteSongTemporarily(songId: String) {
        songToDeleteId = songId
        updateAdapterItems()
    }

    fun cancelDeleteSong() {
        songToDeleteId = null
        updateAdapterItems()
    }

    fun deleteSongPermanently() {
        songToDeleteId?.let {
            songDetailRepository.deleteSong(it)
            songToDeleteId = null
        }
    }
}