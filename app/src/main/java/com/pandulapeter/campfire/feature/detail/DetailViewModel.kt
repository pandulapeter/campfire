package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import android.support.v4.app.FragmentManager
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(
    fragmentManager: FragmentManager,
    currentId: String,
    ids: List<String>,
    private val songInfoRepository: SongInfoRepository,
    private val downloadedSongRepository: DownloadedSongRepository) : CampfireViewModel() {
    val title = ObservableField("")
    val artist = ObservableField("")
    val adapter = SongPagerAdapter(fragmentManager, ids)

    init {
        updateToolbar(currentId)
    }

    fun updateToolbar(currentId: String) {
        songInfoRepository.getLibrarySongs().find { it.id == currentId }?.let {
            downloadedSongRepository.addSongToDownloads(it)
            title.set(it.title)
            artist.set(it.artist)
        }
    }
}