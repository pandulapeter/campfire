package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel
import org.koin.android.ext.android.inject
import java.util.*

class HistoryViewModel(private val openLibrary: () -> Unit) : SongListViewModel(), HistoryRepository.Subscriber {

    private val historyRepository by inject<HistoryRepository>()
    val shouldShowDeleteAll = ObservableBoolean()
    val shouldInvalidateItemDecorations = ObservableBoolean()
    private var songToDeleteId: String? = null
    private var history = listOf<HistoryItem>()
    private val Calendar.year get() = get(Calendar.YEAR)
    private val Calendar.month get() = get(Calendar.MONTH)
    private val Calendar.week get() = get(Calendar.WEEK_OF_YEAR)
    private val Calendar.day get() = get(Calendar.DAY_OF_YEAR)

    init {
        placeholderText.set(R.string.history_placeholder)
        buttonText.set(R.string.go_to_library)
    }

    override fun subscribe() {
        super.subscribe()
        historyRepository.subscribe(this)
    }

    override fun unsubscribe() {
        super.unsubscribe()
        historyRepository.unsubscribe(this)
    }

    override fun Sequence<Song>.createViewModels() = filter { it.id != songToDeleteId }
        .filter { song -> history.firstOrNull { it.id == song.id } != null }
        .sortedByDescending { song -> history.first { it.id == song.id }.lastOpenedAt }
        .map { SongViewModel(songDetailRepository, it) }
        .toList()

    override fun onListUpdated(items: List<SongViewModel>) {
        super.onListUpdated(items)
        shouldShowDeleteAll.set(items.isNotEmpty())
        shouldInvalidateItemDecorations.set(true)
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun onHistoryUpdated(history: List<HistoryItem>) {
        this.history = history
        updateAdapterItems()
    }

    fun isHeader(position: Int) = position == 0 || getHeaderTitle(position) != getHeaderTitle(position - 1)

    fun getHeaderTitle(position: Int): Int {
        val timestamp = history.firstOrNull { it.id == adapter.items[position].song.id }?.lastOpenedAt ?: 0L
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

    fun deleteAllSongs() = historyRepository.deleteAllHistory()

    fun deleteSongTemporarily(songId: String) {
        songToDeleteId = songId
        updateAdapterItems()
    }

    fun cancelDeleteSong() {
        songToDeleteId = null
        updateAdapterItems()
    }

    fun deleteSongPermanently() {
        songToDeleteId?.let {
            historyRepository.deleteHistoryItem(it)
            songToDeleteId = null
        }
    }
}