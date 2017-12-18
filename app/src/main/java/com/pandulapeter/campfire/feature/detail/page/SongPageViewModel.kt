package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [SongPageFragment].
 */
class SongPageViewModel(
    id: String,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository) : CampfireViewModel() {
    val text = ObservableField(id)

    init {
        songInfoRepository.getLibrarySongs().find { it.id == id }?.let { songInfo ->
            downloadedSongRepository.downloadSong(
                songInfo = songInfo,
                onSuccess = {
                    text.set(it)
                },
                onFailure = {
                    text.set("Something went wrong") //TODO: Implement proper error handling.
                })
        }
    }
}