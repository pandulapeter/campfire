package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.SongInfoRepository

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(songInfoRepository: SongInfoRepository, id: String) {
    val title = ObservableField("")
    val artist = ObservableField("")

    init {
        songInfoRepository.getCloudSongs().find { it.id == id }?.let {
            songInfoRepository.addSongToDownloaded(it.id)
            title.set(it.title)
            artist.set(it.artist)
        }
    }
}