package com.pandulapeter.campfire.feature.home.manageDownloads

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager

class ManageDownloadsViewModel(context: Context, private val openLibrary: () -> Unit) : SongListViewModel(context) {

    val shouldShowDeleteAll = ObservableBoolean()
    val songCount = ObservableInt()
    private var songToDeleteId: String? = null
    override val screenName = AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS

    init {
        placeholderText.set(R.string.manage_downloads_placeholder)
        buttonText.set(R.string.go_to_library)
        buttonIcon.set(R.drawable.ic_library_24dp)
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_MANAGE_DOWNLOADS
    }

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
        .toList()

    override fun onListUpdated(items: List<SongListItemViewModel>) {
        super.onListUpdated(items)
        songCount.set(items.size)
        shouldShowDeleteAll.set(items.isNotEmpty())
    }

    override fun onActionButtonClicked() = openLibrary()

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