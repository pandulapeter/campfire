package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Wrapper for locally storing simple key-value pairs. Used for saving user preferences.
 */
class PreferenceStorageManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var lastUpdateTimestamp by LongPreferenceDelegate(sharedPreferences, "last_update_timestamp", 0)
    var isSortedByTitle by BooleanPreferenceDelegate(sharedPreferences, "is_sorted_by_title", true)
    var shouldShowDownloadedOnly by BooleanPreferenceDelegate(sharedPreferences, "should_show_downloaded_only", false)
    var shouldHideExplicit by BooleanPreferenceDelegate(sharedPreferences, "should_hide_explicit", true) //TODO: Add toggle in Settings.
    var shouldHideWorkInProgress by BooleanPreferenceDelegate(sharedPreferences, "should_hide_work_in_progress", !BuildConfig.DEBUG)  //TODO: Add toggle in Settings.
    var shouldShowSongCount by BooleanPreferenceDelegate(sharedPreferences, "should_show_song_count", BuildConfig.DEBUG) //TODO: Add toggle in Settings.
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

    fun isLanguageFilterEnabled(language: Language) = when (language) {
        is Language.Known -> sharedPreferences.getBoolean(KEY_LANGUAGE_FILTER + language.id, true)
        is Language.Unknown -> sharedPreferences.getBoolean(KEY_UNKNOWN_LANGUAGE_FILTER, true)
    }

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) = sharedPreferences.edit().putBoolean(when (language) {
        is Language.Known -> KEY_LANGUAGE_FILTER + language.id
        is Language.Unknown -> KEY_UNKNOWN_LANGUAGE_FILTER
    }, isEnabled).apply()

    private class BooleanPreferenceDelegate(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Boolean) : ReadWriteProperty<Any, Boolean> {

        override fun getValue(thisRef: Any, property: KProperty<*>) = sharedPreferences.getBoolean(key, defaultValue)

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private class LongPreferenceDelegate(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Long) : ReadWriteProperty<Any, Long> {

        override fun getValue(thisRef: Any, property: KProperty<*>) = sharedPreferences.getLong(key, defaultValue)

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) = sharedPreferences.edit().putLong(key, value).apply()
    }

    companion object {
        private const val KEY_NAVIGATION_ITEM = "navigation_item"
        private const val VALUE_LIBRARY = "library"
        private const val VALUE_HISTORY = "history"
        private const val VALUE_SETTINGS = "settings"
        private const val VALUE_PLAYLIST = "playlist_"
        private const val KEY_LANGUAGE_FILTER = "language_filter_"
        private const val KEY_UNKNOWN_LANGUAGE_FILTER = "unknown_language_filter_"
    }
}