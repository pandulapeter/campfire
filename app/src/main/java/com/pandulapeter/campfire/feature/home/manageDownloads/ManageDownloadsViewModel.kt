package com.pandulapeter.campfire.feature.home.manageDownloads

import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel

class ManageDownloadsViewModel : SongListViewModel() {

    val shouldShowDeleteAll = ObservableBoolean()
    val songCount = ObservableInt()
    private var songToDeleteId: String? = null

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .map { SongViewModel(songDetailRepository, it) }
        .toList()

    override fun onListUpdated(items: List<SongViewModel>) {
        super.onListUpdated(items)
        songCount.set(items.size)
        shouldShowDeleteAll.set(items.isNotEmpty())
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