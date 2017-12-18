package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.storage.DataStorageManager

/**
 * Wraps caching and updating [SongInfo] objects that make up the user's history.
 */
class HistoryRepository(private val dataStorageManager: DataStorageManager) : Repository() {

    fun getHistory(): List<String> = TODO()

    fun addToHistory(id: String) {
        TODO()
    }

    fun removeFromHistory(id: String) {
        TODO()
    }

    fun clearHistory() {
        TODO()
    }
}