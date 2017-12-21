package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import android.support.annotation.StringRes
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import java.util.Calendar
import kotlin.math.abs

/**
 * Handles events and logic for [HistoryFragment].
 */
class HistoryViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val shouldShowClearButton = ObservableBoolean(historyRepository.getHistory().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()
    private val Calendar.year get() = get(Calendar.YEAR)
    private val Calendar.month get() = get(Calendar.MONTH)
    private val Calendar.week get() = get(Calendar.WEEK_OF_YEAR)
    private val Calendar.day get() = get(Calendar.DAY_OF_YEAR)

    override fun getAdapterItems() = historyRepository.getHistory()
        .mapNotNull { songInfoRepository.getSongInfo(it.songId) }
        .filterWorkInProgress()
        .filterExplicit()

    override fun onUpdateDone(items: List<SongInfoViewModel>) {
        super.onUpdateDone(items)
        shouldShowClearButton.set(items.isNotEmpty())
    }

    fun isHeader(position: Int) = position == 0 || getHeaderTitle(position) != getHeaderTitle(position - 1)

    @StringRes
    fun getHeaderTitle(position: Int): Int {
        val timestamp = historyRepository.getHistoryForSong(adapter.items[position].songInfo.id)?.timestamp ?: 0
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (abs(now.timeInMillis - then.timeInMillis) < 60 * 60 * 1000) {
            return R.string.history_now
        }
        if (now.year == then.year && now.month == then.month && now.day == then.day) {
            return R.string.history_today
        }
        val yesterday = Calendar.getInstance().apply { timeInMillis = timestamp - 24 * 60 * 60 * 1000 }
        if (now.year == yesterday.year && now.month == yesterday.month && now.day == yesterday.day) {
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

    fun removeSongFromHistory(songId: String) = historyRepository.removeFromHistory(songId)

    fun onClearButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun clearHistory() = historyRepository.clearHistory()
}