package com.pandulapeter.campfire.integration

import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.util.enqueueCall

class AnalyticsManager(private val preferenceDatabase: PreferenceDatabase, private val networkManager: NetworkManager) {

    private fun track() {
        if (preferenceDatabase.shouldShareUsageData) {

        }
    }

    fun onSongOpened(songId: String) {
        if (preferenceDatabase.shouldShareUsageData) {
            networkManager.service.openSong(songId).enqueueCall({}, {})
        }
    }
}