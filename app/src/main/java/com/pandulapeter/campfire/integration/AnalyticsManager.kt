package com.pandulapeter.campfire.integration

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.util.enqueueCall

class AnalyticsManager(context: Context, private val preferenceDatabase: PreferenceDatabase, private val networkManager: NetworkManager) {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    init {
        firebaseAnalytics.setAnalyticsCollectionEnabled(preferenceDatabase.shouldShareUsageData)
    }

    private fun track(event: String, vararg arguments: Pair<String, String>) {
        if (preferenceDatabase.shouldShareUsageData) {
            firebaseAnalytics.logEvent(event, Bundle().apply { arguments.forEach { putString(it.first, it.second) } })
        }
    }

    fun onSongOpened(songId: String) {
        if (preferenceDatabase.shouldShareUsageData) {
            networkManager.service.openSong(songId).enqueueCall({}, {})
        }
        track("song_opened", "song_id" to songId)
    }

    fun onCollectionOpened(collectionId: String) {
        if (preferenceDatabase.shouldShareUsageData) {
            networkManager.service.openCollection(collectionId).enqueueCall({}, {})
        }
        track("collection_opened", "collection_id" to collectionId)
    }
}