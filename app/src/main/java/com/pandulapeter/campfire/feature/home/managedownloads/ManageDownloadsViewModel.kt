package com.pandulapeter.campfire.feature.home.managedownloads

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Handles events and logic for [ManageDownloadsFragment].
 */
class ManageDownloadsViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository) {
    val shouldShowDeleteAllButton = ObservableBoolean(downloadedSongRepository.getDownloadedSongIds().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()
    val totalFileSize = ObservableField("")

    override fun getAdapterItems() = downloadedSongRepository.getDownloadedSongIds()
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .sortedBy { it.titleWithSpecialCharactersRemoved } //TODO: Find a more meaningful way to sort these items (maybe by size).
        .map {
            SongInfoViewModel(
                songInfo = it,
                isSongDownloaded = true,
                isSongLoading = false,
                isSongOnAnyPlaylist = false,
                shouldShowDragHandle = false,
                shouldShowPlaylistButton = false,
                shouldShowDownloadButton = false,
                alertText = null)
        }

    override fun onUpdate(updateType: UpdateType) {
        super.onUpdate(updateType)
        async(UI) {
            totalFileSize.set(async(CommonPool) {
                humanReadableByteCount(downloadedSongRepository.getDownloadCacheSize())
            }.await())
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>) {
        super.onUpdateDone(items)
        shouldShowDeleteAllButton.set(items.isNotEmpty())
    }

    fun onDeleteAllButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun removeSongFromDownloads(songId: String) = downloadedSongRepository.removeSongFromDownloads(songId)

    fun deleteAllDownloads() = downloadedSongRepository.clearDownloads()

    private fun humanReadableByteCount(bytes: Long): String {
        val unit = 1024
        return if (bytes < unit) {
            "$bytes B"
        } else {
            val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
            val pre = "KMGTPE"[exp - 1]
            String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
        }
    }
}