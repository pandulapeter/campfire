package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.data.repository.SongInfoRepository

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(fragmentManager: FragmentManager, songInfoRepository: SongInfoRepository, currentId: String, ids: List<String>) {
    val title = ObservableField("")
    val artist = ObservableField("")
    val adapter = SongPagerAdapter(fragmentManager, ids)
    init {
        songInfoRepository.getCloudSongs().find { it.id == currentId }?.let {
            songInfoRepository.addSongToDownloaded(it.id)
            title.set(it.title)
            artist.set(it.artist)
        }
    }
}