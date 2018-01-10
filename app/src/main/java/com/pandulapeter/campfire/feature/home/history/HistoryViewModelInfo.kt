package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import java.util.Calendar
import kotlin.math.abs

/**
 * Handles events and logic for [HistoryFragmentInfo].
 */
class HistoryViewModelInfo(analyticsManager: AnalyticsManager,
                           songInfoRepository: SongInfoRepository,
                           downloadedSongRepository: DownloadedSongRepository,
                           private val playlistRepository: PlaylistRepository,
                           private val historyRepository: HistoryRepository) : SongInfoListViewModel(analyticsManager, songInfoRepository, downloadedSongRepository) {
    val shouldShowClearButton = ObservableBoolean(historyRepository.getHistoryItems().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()
    val shouldInvalidateItemDecorations = ObservableBoolean()
    val shouldShowHintSnackbar = ObservableBoolean()
    val shouldAllowToolbarScrolling = ObservableBoolean()
    private val Calendar.year get() = get(Calendar.YEAR)
    private val Calendar.month get() = get(Calendar.MONTH)
    private val Calendar.week get() = get(Calendar.WEEK_OF_YEAR)
    private val Calendar.day get() = get(Calendar.DAY_OF_YEAR)

    override fun getAdapterItems() = historyRepository.getHistoryItems()
        .mapNotNull { songInfoRepository.getSongInfo(it.songId) }
        .map { songInfo ->
            val isDownloaded = downloadedSongRepository.isSongDownloaded(songInfo.id)
            val isSongNew = false //TODO: Check if the song is new.
            SongInfoViewModel(
                songInfo = songInfo,
                isSongDownloaded = isDownloaded,
                isSongLoading = downloadedSongRepository.isSongLoading(songInfo.id),
                isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                shouldShowDragHandle = false,
                shouldShowPlaylistButton = true,
                shouldShowDownloadButton = !isDownloaded || isSongNew,
                alertText = if (isDownloaded) {
                    if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                } else {
                    if (isSongNew) R.string.library_new else null
                })
        }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.PlaylistsUpdated,
            is UpdateType.HistoryUpdated,
            is UpdateType.ItemAddedToHistory, //TODO: Call adapter.notifyItemAdded() instead.
            is UpdateType.ItemRemovedFromHistory, //TODO: Call adapter.notifyItemRemoved() instead.
            is UpdateType.HistoryCleared, //TODO: Call adapter.notifyDataSetChanged() instead.
            is UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.SongAddedToDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.SONG_DOWNLOADED) }
            is UpdateType.SongRemovedFromDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.SONG_DOWNLOAD_DELETED) }
            is UpdateType.DownloadStarted -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DOWNLOAD_STARTED) }
            is UpdateType.DownloadSuccessful -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DOWNLOAD_SUCCESSFUL) }
            is UpdateType.DownloadFailed -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DOWNLOAD_FAILED) }
            is UpdateType.SongAddedToPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1 && !adapter.items[it].isSongOnAnyPlaylist) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.SONG_IS_IN_A_PLAYLIST) }
            is UpdateType.SongRemovedFromPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1 && !playlistRepository.isSongInAnyPlaylist(updateType.songId)) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.SONG_IS_NOT_IN_A_PLAYLISTS) }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        shouldShowClearButton.set(items.isNotEmpty())
        shouldAllowToolbarScrolling.set(items.isNotEmpty())
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
        if (abs(now.timeInMillis - then.timeInMillis) < 30 * 60 * 1000) {
            return R.string.history_now
        }
        if (now.year == then.year && now.month == then.month && now.day == then.day) {
            return R.string.history_today
        }
        val yesterday = Calendar.getInstance().apply { timeInMillis -= 24 * 60 * 60 * 1000 }
        if (yesterday.year == then.year && yesterday.month == then.month && yesterday.day == then.day) {
            return R.string.history_yesterday
        }
        return when (abs(now.year - then.year)) {
            0 -> when (abs(now.month - then.month)) {
                0 -> when (abs(now.week - then.week)) {
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

    //TODO: Removing the
    fun removeSongFromHistory(songId: String) = historyRepository.removeFromHistory(songId)

    fun onClearButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun clearHistory() = historyRepository.clearHistory()
}