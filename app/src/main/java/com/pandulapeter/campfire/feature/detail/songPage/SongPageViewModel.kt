package com.pandulapeter.campfire.feature.detail.songPage

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Handles events and logic for [SongPageFragment].
 */
class SongPageViewModel(
    private val id: String,
    analyticsManager: AnalyticsManager,
    private val songInfoRepository: SongInfoRepository,
    private val downloadedSongRepository: DownloadedSongRepository,
    userPreferenceRepository: UserPreferenceRepository
) : CampfireViewModel(analyticsManager), Subscriber {
    val text = ObservableField("")
    val shouldShowPlaceholder = ObservableBoolean()
    val isLoading = ObservableBoolean(downloadedSongRepository.isSongLoading(id))
    private val shouldShowChords = ObservableBoolean(userPreferenceRepository.shouldShowChords)

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.ShouldShowChords -> shouldShowChords.set(updateType.shouldShowChords)
            is UpdateType.DownloadStarted -> if (updateType.songId == id) {
                isLoading.set(true)
                shouldShowPlaceholder.set(false)
            }
            is UpdateType.DownloadSuccessful -> if (updateType.songId == id) {
                async(UI) {
                    async(CommonPool) {
                        text.set(updateType.song.parseSong())
                    }.await()
                    isLoading.set(false)
                }
            }
            is UpdateType.DownloadFailed -> if (updateType.songId == id) {
                isLoading.set(false)
                shouldShowPlaceholder.set(true)
            }
        }
    }

    fun loadSong() {
        if (!downloadedSongRepository.isSongLoading(id)) {
            songInfoRepository.getLibrarySongs().find { it.id == id }?.let { songInfo ->
                downloadedSongRepository.startSongDownload(songInfo)
            }
        }
    }

    //TODO: Implement chord parsing.
    private fun String.parseSong() = this
}