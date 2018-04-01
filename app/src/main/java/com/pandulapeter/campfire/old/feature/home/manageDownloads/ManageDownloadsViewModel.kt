package com.pandulapeter.campfire.old.feature.home.manageDownloads

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.SongInfoRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Handles events and logic for [ManageDownloadsFragment].
 */
class ManageDownloadsViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    userPreferenceRepository: UserPreferenceRepository
) : SongInfoListViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository, userPreferenceRepository) {
    val shouldShowDeleteAllButton = ObservableBoolean(downloadedSongRepository.getDownloadedSongIds().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()
    val shouldShowHintSnackbar = ObservableBoolean()
    val totalFileSize = ObservableField(context?.getString(R.string.manage_downloads_subtitle_calculating) ?: "")
    val totalFileCount = ObservableInt(downloadedSongRepository.downloadedItemCount())

    override fun getAdapterItems() = downloadedSongRepository.getDownloadedSongIds()
        .asSequence()
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .sortedByDescending { downloadedSongRepository.getDownloadSize(it.id) }
        .map { songInfo ->
            SongInfoViewModel(
                songInfo = songInfo,
                downloadState = DownloadedSongRepository.DownloadState.Downloaded.UpToDate,
                isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                text = humanReadableByteCount(downloadedSongRepository.getDownloadSize(songInfo.id))
            )
        }
        .toList()

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.SongRemovedFromDownloads,
            UpdateType.AllDownloadsRemoved,
            is UpdateType.Download.Successful,
            is UpdateType.LibraryCacheUpdated -> super.onUpdate(updateType)
            is UpdateType.SongAddedToPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !adapter.items[it].isSongOnAnyPlaylist) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.IsSongInAPlaylistChanged(true))
            }
            is UpdateType.SongRemovedFromPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !playlistRepository.isSongInAnyPlaylist(updateType.songId)) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.IsSongInAPlaylistChanged(false)
                )
            }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        shouldShowDeleteAllButton.set(items.isNotEmpty())
        if (items.isNotEmpty()) {
            shouldShowHintSnackbar.set(true)
        }
        async(UI) {
            totalFileCount.set(downloadedSongRepository.downloadedItemCount())
            totalFileSize.set(async(CommonPool) {
                humanReadableByteCount(downloadedSongRepository.getDownloadCacheSize())
            }.await())
        }
    }

    fun onDeleteAllButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun removeSongFromDownloads(songId: String) = downloadedSongRepository.removeSongFromDownloads(songId)

    fun deleteAllDownloads() = downloadedSongRepository.clearDownloads()

    private fun humanReadableByteCount(bytes: Long): String {
        val unit = 1024
        return if (bytes < unit) {
            "$bytes B"
        } else {
            val exponent = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
            val prefix = "KMGTPE"[exponent - 1]
            String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exponent.toDouble()), prefix)
        }
    }
}