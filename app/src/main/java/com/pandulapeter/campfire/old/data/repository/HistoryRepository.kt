package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.old.data.model.History
import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.DataStorageManager
import kotlin.properties.Delegates

/**
 * Wraps caching and updating of [History] objects.
 */
class HistoryRepository(private val dataStorageManager: DataStorageManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.history) { _, _, new -> dataStorageManager.history = new }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.HistoryUpdated)
    }

    fun getHistoryItems() = dataSet.values.toList().sortedByDescending { it.timestamp }

    fun getHistoryForSong(songId: String) = dataSet[songId]

    fun addToHistory(songId: String, timestamp: Long = System.currentTimeMillis()) {
        val history = History(songId, timestamp)
        dataSet = dataSet.toMutableMap().apply { put(songId, history) }
        notifySubscribers(UpdateType.ItemAddedToHistory(history, getHistoryItems().indexOf(history)))
    }

    fun removeFromHistory(songId: String) {
        if (dataSet.contains(songId)) {
            dataSet = dataSet.toMutableMap().apply { remove(songId) }
            notifySubscribers(UpdateType.ItemRemovedFromHistory(songId))
        }
    }

    fun clearHistory() {
        dataSet = mapOf()
        notifySubscribers(UpdateType.HistoryCleared)
    }
}