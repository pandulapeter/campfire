package com.pandulapeter.campfire.feature.home.managedownloads

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

/**
 * Handles events and logic for [ManageDownloadsFragment].
 */
class ManageDownloadsViewModel(context: Context?,
                               songInfoRepository: SongInfoRepository,
                               downloadedSongRepository: DownloadedSongRepository) : SongListViewModel(songInfoRepository, downloadedSongRepository) {
    val shouldShowDeleteAllButton = ObservableBoolean()
    val shouldShowConfirmationDialog = ObservableBoolean()
    val shouldShowHintSnackbar = ObservableBoolean()
    val totalFileSize = ObservableField(context?.getString(R.string.manage_downloads_total_size_calculating) ?: "")

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
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.SongRemovedFromDownloads, //TODO: Cal adapter.notifyItemRemoved() instead
            is UpdateType.SongAddedToDownloads, //TODO: Cal adapter.notifyItemAdded() instead
            is UpdateType.AllDownloadsRemoved, //TODO: Cal adapter.notifyDataSetChanged() instead
            is UpdateType.DownloadSuccessful, //TODO: Cal adapter.notifyItemAdded() instead
            is UpdateType.LibraryCacheUpdated -> super.onUpdate(updateType)
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        shouldShowDeleteAllButton.set(items.isNotEmpty())
        if (items.isNotEmpty()) {
            shouldShowHintSnackbar.set(true)
        }
        async(UI) {
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