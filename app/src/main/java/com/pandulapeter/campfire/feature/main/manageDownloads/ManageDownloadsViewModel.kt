package com.pandulapeter.campfire.feature.main.manageDownloads

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListViewModel
import com.pandulapeter.campfire.feature.main.shared.baseSongList.SongListItemViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.removePrefixes

class ManageDownloadsViewModel(context: Context, private val openSongs: () -> Unit) : BaseSongListViewModel(context) {

    val shouldShowDeleteAll = ObservableBoolean()
    val songCount = ObservableInt()
    private var songToDeleteId: String? = null
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS

    init {
        placeholderText.set(R.string.manage_downloads_placeholder)
        buttonText.set(R.string.go_to_songs)
        buttonIcon.set(R.drawable.ic_songs_24dp)
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_MANAGE_DOWNLOADS
    }

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .sortedBy { it.getNormalizedTitle().removePrefixes() }
        .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
        .toList()

    override fun onListUpdated(items: List<SongListItemViewModel>) {
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