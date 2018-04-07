package com.pandulapeter.campfire.feature.home.manageDownloads

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel

class ManageDownloadsViewModel : SongListViewModel() {

    val shouldShowDeleteAll = ObservableBoolean()

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .map { SongViewModel(songDetailRepository, it) }
        .toList()

    fun deleteAllSongs() = songDetailRepository.deleteAllSongs()

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        super.onSongDetailRepositoryDownloadSuccess(songDetail)
        shouldShowDeleteAll.set(true)
    }

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) {
        super.onSongDetailRepositoryUpdated(downloadedSongs)
        shouldShowDeleteAll.set(downloadedSongs.isNotEmpty())
    }
}