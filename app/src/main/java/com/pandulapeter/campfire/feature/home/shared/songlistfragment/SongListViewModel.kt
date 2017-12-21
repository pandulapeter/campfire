package com.pandulapeter.campfire.feature.home.shared.songlistfragment

import android.support.annotation.CallSuper
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Parent class for view models that display lists of songs.
 *
 * Handles events and logic for subclasses of [SongListFragment].
 */
abstract class SongListViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                                 private val userPreferenceRepository: UserPreferenceRepository,
                                 protected val songInfoRepository: SongInfoRepository,
                                 protected val downloadedSongRepository: DownloadedSongRepository,
                                 protected val playlistRepository: PlaylistRepository) : HomeFragmentViewModel(homeCallbacks), Subscriber {
    val adapter = SongInfoAdapter()

    abstract fun getAdapterItems(): List<SongInfo>

    @CallSuper
    override fun onUpdate(updateType: UpdateType) {
        async(UI) {
            onUpdateDone(async(CommonPool) {
                val items = getAdapterItems()
                val shouldShowPlaylistButton = shouldShowPlaylistButton()
                val shouldAllowDownloadButton = shouldAllowDownloadButton()
                val shouldShowDragHandle = shouldShowDragHandle(items.size)
                items.map { songInfo ->
                    val isDownloaded = downloadedSongRepository.isSongDownloaded(songInfo.id)
                    val isSongNew = false //TODO: Check if the song is new.
                    SongInfoViewModel(
                        songInfo = songInfo,
                        isSongDownloaded = isDownloaded,
                        isSongLoading = downloadedSongRepository.isSongLoading(songInfo.id),
                        isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                        shouldShowDragHandle = shouldShowDragHandle,
                        shouldShowPlaylistButton = shouldShowPlaylistButton,
                        shouldShowDownloadButton = shouldAllowDownloadButton && !shouldShowDragHandle && (!isDownloaded || isSongNew),
                        alertText = if (!shouldAllowDownloadButton) null else if (isDownloaded) {
                            if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                        } else {
                            if (isSongNew) R.string.library_new else null
                        })
                }
            }.await())
        }
    }

    //TODO: Display snackbars on success / failure (with Retry action).
    fun downloadSong(songInfo: SongInfo) = downloadedSongRepository.downloadSong(songInfo)

    @CallSuper
    protected open fun onUpdateDone(items: List<SongInfoViewModel>) {
        adapter.items = items
    }

    protected open fun shouldShowDragHandle(itemCount: Int) = false

    open fun shouldShowPlaylistButton() = true

    open fun shouldAllowDownloadButton() = true

    protected fun List<SongInfo>.filterWorkInProgress() = if (userPreferenceRepository.shouldHideWorkInProgress) filter { it.version ?: 0 >= 0 } else this

    protected fun List<SongInfo>.filterExplicit() = if (userPreferenceRepository.shouldHideExplicit) filter { it.isExplicit != true } else this
}