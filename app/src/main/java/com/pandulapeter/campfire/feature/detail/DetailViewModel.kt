package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.ChangeListener
import com.pandulapeter.campfire.data.repository.SongInfoRepository

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(songInfoRepository: SongInfoRepository, id: String, val title: String, val artist: String) {
    init {
        songInfoRepository.getCloudSongs(ChangeListener(
            onNext = {
                it.find { it.id == id }?.let { songInfoRepository.addSongToDownloaded(it) }
            },
            onComplete = {},
            onError = {}
        ))
    }
}