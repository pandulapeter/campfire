package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [SongPageFragment].
 */
class SongPageViewModel(private val id: String,
                        analyticsManager: AnalyticsManager,
                        private val songInfoRepository: SongInfoRepository,
                        private val downloadedSongRepository: DownloadedSongRepository,
                        userPreferenceRepository: UserPreferenceRepository) : CampfireViewModel(analyticsManager), Subscriber {
    val text = ObservableField("")
    val shouldShowChords = ObservableBoolean(userPreferenceRepository.shouldShowChords)
    val shouldShowPlaceholder = ObservableBoolean()
    val isLoading = ObservableBoolean(downloadedSongRepository.isSongLoading(id))

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.ShouldShowChords -> shouldShowChords.set(updateType.shouldShowChords)
            is UpdateType.DownloadStarted -> if (updateType.songId == id) {
                isLoading.set(true)
                shouldShowPlaceholder.set(false)
            }
            is UpdateType.DownloadSuccessful -> if (updateType.songId == id) isLoading.set(false)
            is UpdateType.DownloadFailed -> if (updateType.songId == id) {
                isLoading.set(false)
                shouldShowPlaceholder.set(true)
            }
        }
    }

    fun loadSong() {
        //TODO: Might be better to use the observer pattern instead of passing the lambda.
        songInfoRepository.getLibrarySongs().find { it.id == id }?.let { songInfo ->
            downloadedSongRepository.downloadSong(
                songInfo = songInfo,
                onSuccess = { text.set(it) },
                onFailure = {})
        }
    }
}