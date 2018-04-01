package com.pandulapeter.campfire.old.feature.home.shared.songInfoList

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.annotation.CallSuper
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.SongInfoRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Parent class for view models that display lists of songs.
 *
 * Handles events and logic for subclasses of [SongInfoListFragment].
 */
abstract class SongInfoListViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    protected val songInfoRepository: SongInfoRepository,
    protected val downloadedSongRepository: DownloadedSongRepository,
    protected val playlistRepository: PlaylistRepository,
    protected val userPreferenceRepository: UserPreferenceRepository
) : HomeChildViewModel(analyticsManager), Subscriber {
    val adapter = SongInfoListAdapter()
    val shouldShowDownloadErrorSnackbar = ObservableField<SongInfo?>()
    val shouldShowPlaceholder = ObservableBoolean()
    val shouldNavigateToLibrary = ObservableBoolean()
    private var coroutine: CoroutineContext? = null
    protected val newString = context?.getString(R.string.new_tag) ?: ""
    protected val updateString = context?.getString(R.string.new_version_available) ?: ""

    abstract fun getAdapterItems(): List<SongInfoViewModel>

    override fun onUpdate(updateType: UpdateType) {
        coroutine?.cancel()
        coroutine = async(UI) { onUpdateDone(async(CommonPool) { getAdapterItems() }.await(), updateType) }
    }

    @CallSuper
    protected open fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        adapter.items = items.toMutableList()
        shouldShowPlaceholder.set(items.isEmpty())
    }

    fun navigateToLibrary() = shouldNavigateToLibrary.set(true)

    fun downloadSong(songInfo: SongInfo) = downloadedSongRepository.startSongDownload(
        songInfo = songInfo,
        onFailure = { shouldShowDownloadErrorSnackbar.set(songInfo) })
}