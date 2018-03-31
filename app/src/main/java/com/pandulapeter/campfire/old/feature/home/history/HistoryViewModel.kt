package com.pandulapeter.campfire.old.feature.home.history

import android.content.Context
import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.*
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.old.networking.AnalyticsManager
import java.util.*

/**
 * Handles events and logic for [HistoryFragment].
 */
class HistoryViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    userPreferenceRepository: UserPreferenceRepository,
    private val historyRepository: HistoryRepository
) : SongInfoListViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository, userPreferenceRepository) {
    val shouldShowClearButton = ObservableBoolean(historyRepository.getHistoryItems().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()
    val shouldInvalidateItemDecorations = ObservableBoolean()
    val shouldShowHintSnackbar = ObservableBoolean()
    private val Calendar.year get() = get(Calendar.YEAR)
    private val Calendar.month get() = get(Calendar.MONTH)
    private val Calendar.week get() = get(Calendar.WEEK_OF_YEAR)
    private val Calendar.day get() = get(Calendar.DAY_OF_YEAR)

    override fun getAdapterItems() = historyRepository.getHistoryItems()
        .asSequence()
        .mapNotNull { songInfoRepository.getSongInfo(it.songId) }
        .map { songInfo ->
            SongInfoViewModel(
                songInfo = songInfo,
                downloadState = downloadedSongRepository.getSongDownloadedState(songInfo.id),
                isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                updateText = updateString,
                newText = newString
            )
        }
        .toList()

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.PlaylistsUpdated,
            UpdateType.HistoryUpdated,
            is UpdateType.ItemAddedToHistory,
            is UpdateType.ItemRemovedFromHistory,
            UpdateType.HistoryCleared,
            UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.Download -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DownloadStateChanged(downloadedSongRepository.getSongDownloadedState(updateType.songId)))
            }
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
        shouldShowClearButton.set(items.isNotEmpty())
        shouldInvalidateItemDecorations.set(true)
        if (items.isNotEmpty()) {
            shouldShowHintSnackbar.set(true)
        }
    }

    fun isHeader(position: Int) = position == 0 || getHeaderTitle(position) != getHeaderTitle(position - 1)

    fun getHeaderTitle(position: Int): Int {
        val timestamp = historyRepository.getHistoryForSong(adapter.items[position].songInfo.id)?.timestamp ?: 0
        if (timestamp == 0L) {
            return 0
        }
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (Math.abs(now.timeInMillis - then.timeInMillis) < 30 * 60 * 1000) {
            return R.string.history_now
        }
        if (now.year == then.year && now.month == then.month && now.day == then.day) {
            return R.string.history_today
        }
        val yesterday = Calendar.getInstance().apply { timeInMillis -= 24 * 60 * 60 * 1000 }
        if (yesterday.year == then.year && yesterday.month == then.month && yesterday.day == then.day) {
            return R.string.history_yesterday
        }
        return when (Math.abs(now.year - then.year)) {
            0 -> when (Math.abs(now.month - then.month)) {
                0 -> when (Math.abs(now.week - then.week)) {
                    0 -> R.string.history_this_week
                    1 -> R.string.history_last_week
                    else -> R.string.history_this_month
                }
                1 -> R.string.history_last_month
                else -> R.string.history_this_year
            }
            1 -> R.string.history_last_year
            else -> R.string.history_a_long_time_ago
        }
    }

    fun removeSongFromHistory(songId: String) = historyRepository.removeFromHistory(songId)

    fun onClearButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun clearHistory() = historyRepository.clearHistory()
}