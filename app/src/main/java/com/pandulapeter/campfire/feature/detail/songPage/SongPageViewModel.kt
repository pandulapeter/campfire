package com.pandulapeter.campfire.feature.detail.songPage

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Handles events and logic for [SongPageFragment].
 */
class SongPageViewModel(
    val songId: String,
    analyticsManager: AnalyticsManager,
    private val songInfoRepository: SongInfoRepository,
    private val downloadedSongRepository: DownloadedSongRepository,
    private val detailEventBus: DetailEventBus,
    userPreferenceRepository: UserPreferenceRepository
) : CampfireViewModel(analyticsManager), Subscriber {
    val text = ObservableField("")
    val shouldShowPlaceholder = ObservableBoolean()
    val scrollSpeed = ObservableInt()
    val shouldScrollToTop = ObservableBoolean()
    val isLoading = ObservableBoolean(downloadedSongRepository.isSongLoading(songId))
    val transposition = ObservableInt() //TODO: Persist this value per song.
    private val shouldShowChords = userPreferenceRepository.shouldShowChords

    init {
        transposition.onPropertyChanged {
            var modifiedValue = it
            while (modifiedValue > 6) {
                modifiedValue -= 12
            }
            while (modifiedValue < -6) {
                modifiedValue += 12
            }
            if (modifiedValue != it) {
                transposition.set(modifiedValue)
            }
        }
        detailEventBus.songTransposed(songId, transposition.get())
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadStarted -> if (updateType.songId == songId) {
                isLoading.set(true)
                shouldShowPlaceholder.set(false)
            }
            is UpdateType.DownloadSuccessful -> if (updateType.songId == songId) {
                async(UI) {
                    async(CommonPool) {
                        text.set(updateType.song.parseSong())
                    }.await()
                    isLoading.set(false)
                }
            }
            is UpdateType.DownloadFailed -> if (updateType.songId == songId) {
                isLoading.set(false)
                shouldShowPlaceholder.set(true)
            }
            is UpdateType.TransposeEvent -> if (updateType.songId == songId) {
                transposition.set(transposition.get() + updateType.transposeBy)
                //TODO: Save the transposed value.
                detailEventBus.songTransposed(songId, transposition.get())
            }
            is UpdateType.ScrollStarted -> if (updateType.songId == songId) {
                shouldScrollToTop.set(true)
            }
            is UpdateType.ContentScrolled -> if (updateType.songId == songId) {
                scrollSpeed.set(updateType.scrollSpeed)
            }
        }
    }

    fun loadSong() {
        if (!downloadedSongRepository.isSongLoading(songId)) {
            songInfoRepository.getLibrarySongs().find { it.id == songId }?.let { songInfo ->
                downloadedSongRepository.startSongDownload(songInfo)
            }
        }
    }

    //TODO: Implement chord parsing.
    private fun String.parseSong() = this
}