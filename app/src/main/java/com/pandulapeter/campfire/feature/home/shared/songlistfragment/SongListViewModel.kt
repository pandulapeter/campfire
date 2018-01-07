package com.pandulapeter.campfire.feature.home.shared.songlistfragment

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.annotation.CallSuper
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Parent class for view models that display lists of songs.
 *
 * Handles events and logic for subclasses of [SongListFragment].
 */
abstract class SongListViewModel(analyticsManager: AnalyticsManager,
                                 protected val songInfoRepository: SongInfoRepository,
                                 protected val downloadedSongRepository: DownloadedSongRepository) : HomeChildViewModel(analyticsManager), Subscriber {
    val adapter = SongInfoAdapter()
    val shouldShowDownloadErrorSnackbar = ObservableField<SongInfo?>()
    val shouldShowPlaceholder = ObservableBoolean()
    val shouldNavigateToLibrary = ObservableBoolean()

    abstract fun getAdapterItems(): List<SongInfoViewModel>

    override fun onUpdate(updateType: UpdateType) {
        async(UI) { onUpdateDone(async(CommonPool) { getAdapterItems() }.await(), updateType) }
    }

    @CallSuper
    protected open fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        adapter.items = items
        shouldShowPlaceholder.set(items.isEmpty())
    }

    fun navigateToLibrary() = shouldNavigateToLibrary.set(true)

    fun downloadSong(songInfo: SongInfo) = downloadedSongRepository.downloadSong(
        songInfo = songInfo,
        onFailure = { shouldShowDownloadErrorSnackbar.set(songInfo) })
}