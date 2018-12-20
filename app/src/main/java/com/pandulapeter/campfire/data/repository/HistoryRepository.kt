package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.repository.shared.BaseRepository
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryRepository(private val database: Database) : BaseRepository<HistoryRepository.Subscriber>() {
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
        data.swap(data.filter { it.id != historyItem.id })
        data.add(historyItem)
        notifyDataChanged()
        GlobalScope.launch(WORKER) { database.historyDao().insert(historyItem) }
    }

    fun deleteHistoryItem(songId: String) {
        data.swap(data.filter { it.id != songId })
        notifyDataChanged()
        GlobalScope.launch(WORKER) { database.historyDao().delete(songId) }
    }

    fun deleteAllHistory() {
        data.clear()
        notifyDataChanged()
        GlobalScope.launch(WORKER) { database.historyDao().deleteAll() }
    }

    private fun refreshDataSet() {
        GlobalScope.launch(UI) {
            withContext(WORKER) { database.historyDao().getAll() }.let {
                data.swap(it)
                isCacheLoaded = true
                notifyDataChanged()
            }
        }
    }

    private fun notifyDataChanged() = GlobalScope.launch(UI) { subscribers.forEach { it.onHistoryUpdated(data) } }

    interface Subscriber {

        fun onHistoryUpdated(history: List<HistoryItem>)
    }
}