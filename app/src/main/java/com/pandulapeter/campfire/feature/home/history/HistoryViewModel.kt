package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel
import org.koin.android.ext.android.inject

class HistoryViewModel(private val openLibrary: () -> Unit) : SongListViewModel(), HistoryRepository.Subscriber {

    private val historyRepository by inject<HistoryRepository>()
    val shouldShowDeleteAll = ObservableBoolean()
    private var songToDeleteId: String? = null
    private var history = listOf<HistoryItem>()

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

    override fun Sequence<Song>.createViewModels() = filter { songDetailRepository.isSongDownloaded(it.id) }
        .filter { it.id != songToDeleteId }
        .filter { song -> history.firstOrNull { it.id == song.id } != null }
        .sortedByDescending { song -> history.first { it.id == song.id }.lastOpenedAt }
        .map { SongViewModel(songDetailRepository, it) }
        .toList()

    override fun onListUpdated(items: List<SongViewModel>) {
        super.onListUpdated(items)
        shouldShowDeleteAll.set(items.isNotEmpty())
    }

    override fun onActionButtonClicked() = openLibrary()

    override fun onHistoryUpdated(history: List<HistoryItem>) {
        this.history = history
        updateAdapterItems()
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