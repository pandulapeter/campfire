package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.feature.home.HomeViewModel


/**
 * Wrapper for locally storing simple key-value pairs. Used for saving user preferences.
 */
class PreferenceStorageManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /**
     * The last selected item from the home screen's side navigation.
     */
    var navigationItem: HomeViewModel.NavigationItem
        get() {
            sharedPreferences.getString(KEY_NAVIGATION_ITEM, VALUE_LIBRARY).let {
                return when (it) {
                    VALUE_LIBRARY -> HomeViewModel.NavigationItem.Library
                    VALUE_HISTORY -> HomeViewModel.NavigationItem.History
                    VALUE_SETTINGS -> HomeViewModel.NavigationItem.Settings
                    else -> HomeViewModel.NavigationItem.Playlist(Integer.parseInt(it.removePrefix(VALUE_PLAYLIST)))
                }
            }
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_NAVIGATION_ITEM, when (value) {
                HomeViewModel.NavigationItem.Library -> VALUE_LIBRARY
                HomeViewModel.NavigationItem.History -> VALUE_HISTORY
                HomeViewModel.NavigationItem.Settings -> VALUE_SETTINGS
                is HomeViewModel.NavigationItem.Playlist -> VALUE_PLAYLIST + value.id
            }).apply()
        }

    /**
     * The timestamp of the most recent update that helps to determine how old is the local cache.
     *
     * TODO: Remove duplicated code using delegation.
     */
    var lastUpdateTimestamp: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_TIMESTAMP, 0)
        set(value) {
            sharedPreferences.edit().putLong(KEY_LAST_UPDATE_TIMESTAMP, value).apply()
        }

    /**
     * Whether or not the user's last saved preference was to sort the songs by title.
     */
    var isSortedByTitle: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_SORTED_BY_TITLE, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_IS_SORTED_BY_TITLE, value).apply()
        }

    /**
     * Whether or not to hide songs from the cloud.
     */
    var shouldShowDownloadedOnly: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOULD_SHOW_DOWNLOADED_ONLY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOULD_SHOW_DOWNLOADED_ONLY, value).apply()
        }

    /**
     * Whether or not explicit songs should be filtered out.
     */
    var shouldHideExplicit: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOULD_HIDE_EXPLICIT, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOULD_HIDE_EXPLICIT, value).apply()
        }

    /**
     * Whether or not work-in-progress songs should be filtered out.
     */
    var shouldHideWorkInProgress: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOULD_HIDE_WORK_IN_PROGRESS, !BuildConfig.DEBUG)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOULD_HIDE_WORK_IN_PROGRESS, value).apply()
        }

    /**
     * Whether or not work-in-progress songs should be filtered out.
     */
    var shouldShowSongCount: Boolean
        get() = sharedPreferences.getBoolean(KEY_SHOULD_SHOW_SONG_COUNT, BuildConfig.DEBUG)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_SHOULD_SHOW_SONG_COUNT, value).apply()
        }

    fun isLanguageFilterEnabled(language: Language) = when (language) {
        is Language.Known -> sharedPreferences.getBoolean(KEY_LANGUAGE_FILTER + language.id, true)
        is Language.Unknown -> sharedPreferences.getBoolean(KEY_UNKNOWN_LANGUAGE_FILTER, true)
    }

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) = sharedPreferences.edit().putBoolean(when (language) {
        is Language.Known -> KEY_LANGUAGE_FILTER + language.id
        is Language.Unknown -> KEY_UNKNOWN_LANGUAGE_FILTER
    }, isEnabled).apply()

    companion object {
        private const val KEY_NAVIGATION_ITEM = "navigation_item"
        private const val VALUE_LIBRARY = "library"
        private const val VALUE_HISTORY = "history"
        private const val VALUE_SETTINGS = "settings"
        private const val VALUE_PLAYLIST = "playlist_"
        private const val KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
        private const val KEY_IS_SORTED_BY_TITLE = "is_sorted_by_title"
        private const val KEY_SHOULD_SHOW_DOWNLOADED_ONLY = "should_show_downloaded_only"
        private const val KEY_SHOULD_HIDE_EXPLICIT = "should_hide_explicit"
        private const val KEY_SHOULD_HIDE_WORK_IN_PROGRESS = "should_hide_work_in_progress"
        private const val KEY_LANGUAGE_FILTER = "language_filter_"
        private const val KEY_UNKNOWN_LANGUAGE_FILTER = "unknown_language_filter_"
        private const val KEY_SHOULD_SHOW_SONG_COUNT = "should_show_song_count"
    }
}