package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.History
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.DataStorageManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of [History] objects.
 */
class HistoryRepository(private val dataStorageManager: DataStorageManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.history) { _: KProperty<*>, old: Map<String, History>, new: Map<String, History> ->
        async(CommonPool) {
            if (old != new) {
                //TODO: If only a single line has been changed, we should not rewrite the entire map.
                dataStorageManager.history = new
            }
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.HistoryUpdated(getHistoryItems()))
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