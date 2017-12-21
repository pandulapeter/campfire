package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.History
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.DataStorageManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of [History] objects.
 */
class HistoryRepository(private val dataStorageManager: DataStorageManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.history) { _: KProperty<*>, old: Map<String, History>, new: Map<String, History> ->
        if (old != new) {
            notifySubscribers(UpdateType.HistoryUpdated(new.values.toList()))
            dataStorageManager.history = new
        }
    }

    fun getHistory() = dataSet.values.toList().sortedByDescending { it.timestamp }

    fun getHistoryForSong(songId: String) = dataSet[songId]

    fun addToHistory(id: String, timestamp: Long = System.currentTimeMillis()) {
        dataSet = dataSet.toMutableMap().apply { put(id, History(id, timestamp)) }
    }

    fun removeFromHistory(id: String) {
        if (dataSet.contains(id)) {
            dataSet = dataSet.toMutableMap().apply { remove(id) }
        }
    }

    fun clearHistory() {
        dataSet = mapOf()
    }
}