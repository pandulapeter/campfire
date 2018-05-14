package com.pandulapeter.campfire.integration

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.util.enqueueCall

class AnalyticsManager(context: Context, private val preferenceDatabase: PreferenceDatabase, private val networkManager: NetworkManager) {

    companion object {

        // Events
        private const val EVENT_APP_OPENED = "app_opened"
        private const val EVENT_SCREEN_OPENED = "screen_opened"
        private const val EVENT_SONG_VISUALIZED = "song_visualized"
        private const val EVENT_PLAYLIST_CREATED = "playlist_created"

        // Keys
        private const val PARAM_KEY_SCREEN = "screen"
        private const val PARAM_KEY_FROM_APP_SHORTCUT = "from_app_shortcut"
        private const val PARAM_KEY_COLLECTION_ID = "collection_id"
        private const val PARAM_KEY_SONG_ID = "song_id"
        private const val PARAM_KEY_SONG_COUNT = "song_count"
        private const val PARAM_KEY_TAB = "tab"
        private const val PARAM_KEY_PLAYLIST_TITLE = "title"

        // Values
        const val PARAM_VALUE_SCREEN_LIBRARY = "library"
        const val PARAM_VALUE_SCREEN_COLLECTIONS = "collections"
        const val PARAM_VALUE_SCREEN_HISTORY = "history"
        const val PARAM_VALUE_SCREEN_OPTIONS = "options"
        const val PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES = "preferences"
        const val PARAM_VALUE_SCREEN_OPTIONS_WHAT_IS_NEW = "what_is_new"
        const val PARAM_VALUE_SCREEN_OPTIONS_ABOUT = "about"
        const val PARAM_VALUE_SCREEN_PLAYLIST = "playlist"
        const val PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS = "manage_playlists"
        const val PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS = "manage_downloads"
        const val PARAM_VALUE_SCREEN_COLLECTION_DETAIL = "collection_detail"
        const val PARAM_VALUE_SCREEN_SONG_DETAIL = "song_detail"
    }

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    init {
        firebaseAnalytics.setAnalyticsCollectionEnabled(preferenceDatabase.shouldShareUsageData)
    }

    fun onAppOpened(screen: String, fromAppShortcut: Boolean) =
        track(EVENT_APP_OPENED, PARAM_KEY_SCREEN to screen, PARAM_KEY_FROM_APP_SHORTCUT to fromAppShortcut.toString())

    fun onTopLevelScreenOpened(screen: String) =
        track(EVENT_SCREEN_OPENED, PARAM_KEY_SCREEN to screen)

    fun onCollectionDetailScreenOpened(collectionId: String) {
        if (preferenceDatabase.shouldShareUsageData) {
            networkManager.service.openCollection(collectionId).enqueueCall({}, {})
        }
        track(EVENT_SCREEN_OPENED, PARAM_KEY_SCREEN to PARAM_VALUE_SCREEN_COLLECTION_DETAIL, PARAM_KEY_COLLECTION_ID to collectionId)
    }

    fun onOptionsScreenOpened(tab: String) =
        track(EVENT_SCREEN_OPENED, PARAM_KEY_SCREEN to PARAM_VALUE_SCREEN_OPTIONS, PARAM_KEY_TAB to tab)

    fun onSongDetailScreenOpened(numberOfSongs: Int) =
        track(EVENT_SCREEN_OPENED, PARAM_KEY_SCREEN to PARAM_VALUE_SCREEN_SONG_DETAIL, PARAM_KEY_SONG_COUNT to numberOfSongs.toString())

    fun onSongVisualized(songId: String) {
        if (preferenceDatabase.shouldShareUsageData) {
            networkManager.service.openSong(songId).enqueueCall({}, {})
        }
        track(EVENT_SONG_VISUALIZED, PARAM_KEY_SONG_ID to songId)
    }

    fun onPlaylistCreated(title: String) =
        track(EVENT_PLAYLIST_CREATED, PARAM_KEY_PLAYLIST_TITLE to title)

    private fun track(event: String, vararg arguments: Pair<String, String>) {
        if (preferenceDatabase.shouldShareUsageData) {
            @Suppress("ConstantConditionIf")
            if (BuildConfig.BUILD_TYPE == "release") {
                firebaseAnalytics.logEvent(event, Bundle().apply { arguments.forEach { putString(it.first, it.second) } })
            } else {
                var text = event
                if (arguments.isNotEmpty()) {
                    text += "(" + arguments.joinToString("; ") { it.first + ": " + it.second } + ")"
                }
                Log.d("ANALYTICS_EVENT", text)
            }
        }
    }
}