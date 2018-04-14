package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.persistence.SongDatabase
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async

class HistoryRepository(private val songDatabase: SongDatabase) : Repository<HistoryRepository.Subscriber>() {
    private val data = mutableListOf<HistoryItem>()
    private var isCacheLoaded = false

    init {
        refreshDataSet()
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onHistoryUpdated(data)
    }

    fun isCacheLoaded() = isCacheLoaded

    fun addHistoryItem(historyItem: HistoryItem) {
        async(UI) {
            data.swap(data.filter { it.id != historyItem.id })
            data.add(historyItem)
            async(CommonPool) { songDatabase.historyDao().insert(historyItem) }.await()
            notifyDataChanged()
        }
    }

    fun deleteHistoryItem(songId: String) {
        data.swap(data.filter { it.id != songId })
        async(UI) {
            async(CommonPool) { songDatabase.historyDao().delete(songId) }.await()
            notifyDataChanged()
        }
    }

    fun deleteAllHistory() {
        data.clear()
        async(UI) {
            async(CommonPool) { songDatabase.historyDao().deleteAll() }.await()
            notifyDataChanged()
        }
    }

    private fun refreshDataSet() {
        async(UI) {
            async(CommonPool) {
                songDatabase.historyDao().getAll()
            }.await().let {
                data.swap(it)
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = subscribers.forEach { it.onHistoryUpdated(data) }

    interface Subscriber {

        fun onHistoryUpdated(history: List<HistoryItem>)
    }
}