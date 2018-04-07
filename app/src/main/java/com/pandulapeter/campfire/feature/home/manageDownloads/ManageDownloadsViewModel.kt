package com.pandulapeter.campfire.feature.home.manageDownloads

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel

class ManageDownloadsViewModel : SongListViewModel() {

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .map { SongViewModel(songDetailRepository, it) }
        .toList()

    fun deleteAllSongs() = songDetailRepository.deleteAllSongs()
}