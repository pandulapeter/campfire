package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.DataStorageManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of song ID-s that make up the user's history.
 */
class HistoryRepository(private val dataStorageManager: DataStorageManager) : Repository() {
    private var dataSet by Delegates.observable(dataStorageManager.history) { _: KProperty<*>, old: List<String>, new: List<String> ->
        if (old != new) {
            notifySubscribers(UpdateType.HistoryUpdated(new))
            dataStorageManager.history = new
        }
    }

    fun getHistory() = List(dataSet.size) { dataSet[it] }

    fun addToHistory(id: String, position: Int = 0) {
        dataSet = dataSet.toMutableList().apply { add(position, id) }.distinct()
    }

    fun removeFromHistory(id: String) {
        if (dataSet.contains(id)) {
            dataSet = dataSet.toMutableList().apply { remove(id) }
        }
    }

    fun clearHistory() {
        dataSet = listOf()
    }
}