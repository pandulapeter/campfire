package com.pandulapeter.campfire.integration

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.util.enqueueCall
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsManager(context: Context, private val preferenceDatabase: PreferenceDatabase, private val networkManager: NetworkManager) {

    companion object {

        // Events
        private const val EVENT_CONSENT_GIVEN = "consent_given"
        private const val EVENT_APP_OPENED = "app_opened"
        private const val EVENT_SCREEN_OPENED = "screen_opened"
        private const val EVENT_SONG_VISUALIZED = "song_visualized"
        private const val EVENT_PLAYLIST_CREATED = "playlist_created"
        private const val EVENT_COLLECTION_BOOKMARKED_STATE_CHANGED = "collection_bookmarked_state_changed"
        private const val EVENT_COLLECTION_SORTING_MODE_UPDATED = "collection_sorting_mode_updated"
        private const val EVENT_COLLECTION_FILTER_TOGGLED = "collection_filter_toggled"
        private const val EVENT_LIBRARY_SORTING_MODE_UPDATED = "library_sorting_mode_updated"
        private const val EVENT_LIBRARY_FILTER_TOGGLED = "library_filter_toggled"

        // Keys
        private const val PARAM_KEY_TIMESTAMP = "timestamp"
        private const val PARAM_KEY_SCREEN = "screen"
        private const val PARAM_KEY_FROM_APP_SHORTCUT = "from_app_shortcut"
        private const val PARAM_KEY_COLLECTION_ID = "collection_id"
        private const val PARAM_KEY_SONG_ID = "song_id"
        private const val PARAM_KEY_SONG_COUNT = "song_count"
        private const val PARAM_KEY_TAB = "tab"
        private const val PARAM_KEY_PLAYLIST_TITLE = "title"
        private const val PARAM_KEY_SOURCE = "source"
        private const val PARAM_KEY_SORTING_MODE = "sorting_mode"
        private const val PARAM_KEY_FILTER = "filter"
        private const val PARAM_KEY_STATE = "state"
        private const val PARAM_KEY_IS_BOOKMARKED = "is_bookmarked"

        // Values
        const val PARAM_VALUE_SCREEN_LIBRARY = "songs"
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
        const val PARAM_VALUE_DRAWER = "drawer"
        const val PARAM_VALUE_BOTTOM_SHEET = "bottom_sheet"
        const val PARAM_VALUE_FLOATING_ACTION_BUTTON = "floating_action_button"
        const val PARAM_VALUE_BY_TITLE = "by_title"
        const val PARAM_VALUE_BY_DATE = "by_date"
        const val PARAM_VALUE_BY_POPULARITY = "by_popularity"
        const val PARAM_VALUE_BY_ARTIST = "by_artist"
        const val PARAM_VALUE_FILTER_BOOKMARKED_ONLY = "bookmarked_only"
        const val PARAM_VALUE_FILTER_SHOW_EXPLICIT = "show_explicit"
        const val PARAM_VALUE_FILTER_LANGUAGE = "language_"
        const val PARAM_VALUE_FILTER_DOWNLOADED_ONLY = "downloaded_only"
        const val PARAM_VALUE_ON = "on"
        const val PARAM_VALUE_OFF = "off"
        const val PARAM_VALUE_YES = "yes"
        const val PARAM_VALUE_NO = "no"
    }

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    init {
        firebaseAnalytics.setAnalyticsCollectionEnabled(preferenceDatabase.shouldShareUsageData)
    }

    fun onConsentGiven() =
        track(EVENT_CONSENT_GIVEN, PARAM_KEY_TIMESTAMP to SimpleDateFormat("yyyy.MM.dd', 'HH:mm:ss z", Locale.ENGLISH).format(Date()))

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

    fun onPlaylistCreated(title: String, source: String) =
        track(EVENT_PLAYLIST_CREATED, PARAM_KEY_PLAYLIST_TITLE to title, PARAM_KEY_SOURCE to source)

    fun onCollectionBookmarkedStateChanged(collectionId: String, isBookmarked: Boolean, source: String) =
        track(
            EVENT_COLLECTION_BOOKMARKED_STATE_CHANGED,
            PARAM_KEY_COLLECTION_ID to collectionId,
            PARAM_KEY_IS_BOOKMARKED to if (isBookmarked) PARAM_VALUE_YES else PARAM_VALUE_NO,
            PARAM_KEY_SOURCE to source
        )

    fun onCollectionSortingModeUpdated(sortingMode: String) =
        track(EVENT_COLLECTION_SORTING_MODE_UPDATED, PARAM_KEY_SORTING_MODE to sortingMode)

    fun onCollectionFilterToggled(filter: String, state: Boolean) =
        track(EVENT_COLLECTION_FILTER_TOGGLED, PARAM_KEY_FILTER to filter, PARAM_KEY_STATE to if (state) PARAM_VALUE_ON else PARAM_VALUE_OFF)

    fun onLibrarySortingModeUpdated(sortingMode: String) =
        track(EVENT_LIBRARY_SORTING_MODE_UPDATED, PARAM_KEY_SORTING_MODE to sortingMode)

    fun onLibraryFilterToggled(filter: String, state: Boolean) =
        track(EVENT_LIBRARY_FILTER_TOGGLED, PARAM_KEY_FILTER to filter, PARAM_KEY_STATE to if (state) PARAM_VALUE_ON else PARAM_VALUE_OFF)

    private fun track(event: String, vararg arguments: Pair<String, String>) {
        if (preferenceDatabase.shouldShareUsageData) {
            @Suppress("ConstantConditionIf")
            if (BuildConfig.BUILD_TYPE == "debug") {
                var text = event
                if (arguments.isNotEmpty()) {
                    text += "(" + arguments.joinToString("; ") { it.first + ": " + it.second } + ")"
                }
                Log.d("ANALYTICS_EVENT", text)
            } else {
                firebaseAnalytics.logEvent(event, Bundle().apply { arguments.forEach { putString(it.first, it.second) } })
            }
        }
    }
}