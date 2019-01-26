package com.pandulapeter.campfire.feature.main.manageDownloads

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.mutableLiveDataOf
import java.text.Collator

class ManageDownloadsViewModel(
    context: Context,
    songRepository: SongRepository,
    songDetailRepository: SongDetailRepository,
    preferenceDatabase: PreferenceDatabase,
    playlistRepository: PlaylistRepository,
    analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : BaseSongListViewModel(context, songRepository, songDetailRepository, preferenceDatabase, playlistRepository, analyticsManager, interactionBlocker) {

    val shouldShowDeleteAll = mutableLiveDataOf(false)
    val songCount = mutableLiveDataOf(0)
    val shouldOpenSongs = MutableLiveData<Boolean?>()
    private var songToDeleteId: String? = null
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS
    override val placeholderText = R.string.manage_downloads_placeholder
    override val buttonIcon = R.drawable.ic_songs

    init {
        buttonText.value = R.string.go_to_songs
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_MANAGE_DOWNLOADS
    }

    override fun Sequence<Song>.createViewModels() = Collator.getInstance().let { collator ->
        filter { songDetailRepository.isSongDownloaded(it.id) }
            .filter { it.id != songToDeleteId }
            .sortedWith(Comparator { s1, s2 -> collator.compare(s1.artist, s2.artist) })
            .sortedWith(Comparator { s1, s2 -> collator.compare(s1.title, s2.title) })
            .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
            .toList()
    }

    override fun onListUpdated(items: List<ItemViewModel>) {
        super.onListUpdated(items)
        songCount.value = items.size
        shouldShowDeleteAll.value = items.isNotEmpty()
    }

    override fun onActionButtonClicked() {
        shouldOpenSongs.value = true
    }

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