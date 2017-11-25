package com.pandulapeter.campfire.feature.home.downloaded

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel

/**
 * Handles events and logic for [DownloadedFragment].
 */
class DownloadedViewModel(private val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()
    val isLoading = ObservableBoolean(false)

    init {
        adapter.items = songInfoRepository.getDownloaded().map { SongInfoViewModel(it, false, true) }
    }
}