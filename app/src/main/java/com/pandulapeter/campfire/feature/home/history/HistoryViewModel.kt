package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel

/**
 * Handles events and logic for [HistoryFragment].
 */
class HistoryViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val shouldShowClearButton = ObservableBoolean(historyRepository.getHistory().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()

    override fun getAdapterItems() = historyRepository.getHistory()
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .filterWorkInProgress()
        .filterExplicit()

    override fun onUpdate(updateType: UpdateType) {
        super.onUpdate(updateType)
        shouldShowClearButton.set(isAdapterNotEmpty)
    }

    fun removeSongFromHistory(songId: String) = historyRepository.removeFromHistory(songId)

    fun onClearButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun clearHistory() = historyRepository.clearHistory()
}