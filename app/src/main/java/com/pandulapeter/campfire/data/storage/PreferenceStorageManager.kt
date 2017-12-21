package com.pandulapeter.campfire.data.storage

import android.content.Context
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Wrapper for locally storing simple key-value pairs. Used for saving user preferences.
 */
class PreferenceStorageManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("preference_storage", Context.MODE_PRIVATE)
    var lastUpdateTimestamp by PreferenceFieldDelegate.Long("last_update_timestamp")
    var isSortedByTitle by PreferenceFieldDelegate.Boolean("is_sorted_by_title", true)
    var shouldShowDownloadedOnly by PreferenceFieldDelegate.Boolean("should_show_downloaded_only")
    var shouldHideExplicit by PreferenceFieldDelegate.Boolean("should_hide_explicit", true) //TODO: Add toggle in Settings.
    var shouldHideWorkInProgress by PreferenceFieldDelegate.Boolean("should_hide_work_in_progress", !BuildConfig.DEBUG)  //TODO: Add toggle in Settings.
    var shouldShowSongCount by PreferenceFieldDelegate.Boolean("should_show_song_count", BuildConfig.DEBUG) //TODO: Add toggle in Settings.
    var navigationItem: HomeViewModel.NavigationItem
        get() {
            preferences.getString(KEY_NAVIGATION_ITEM, VALUE_LIBRARY).let {
                return when (it) {
                    VALUE_LIBRARY -> HomeViewModel.NavigationItem.Library
                    VALUE_COLLECTIONS -> HomeViewModel.NavigationItem.Collections
                    VALUE_HISTORY -> HomeViewModel.NavigationItem.History
                    VALUE_SETTINGS -> HomeViewModel.NavigationItem.Settings
                    VALUE_MANAGE_PLAYLISTS -> HomeViewModel.NavigationItem.ManagePlaylists
                    VALUE_MANAGE_DOWNLOADS -> HomeViewModel.NavigationItem.ManageDownloads
                    else -> HomeViewModel.NavigationItem.Playlist(Integer.parseInt(it.removePrefix(VALUE_PLAYLIST)))
                }
            }
        }
        set(value) {
            preferences.edit().putString(KEY_NAVIGATION_ITEM, when (value) {
                HomeViewModel.NavigationItem.Library -> VALUE_LIBRARY
                HomeViewModel.NavigationItem.Collections -> VALUE_COLLECTIONS
                HomeViewModel.NavigationItem.History -> VALUE_HISTORY
                HomeViewModel.NavigationItem.Settings -> VALUE_SETTINGS
                is HomeViewModel.NavigationItem.Playlist -> VALUE_PLAYLIST + value.id
                HomeViewModel.NavigationItem.ManagePlaylists -> VALUE_MANAGE_PLAYLISTS
                HomeViewModel.NavigationItem.ManageDownloads -> VALUE_MANAGE_DOWNLOADS
            }).apply()
        }

    fun isLanguageFilterEnabled(language: Language) = when (language) {
        is Language.Known -> preferences.getBoolean(KEY_LANGUAGE_FILTER + language.id, true)
        is Language.Unknown -> preferences.getBoolean(KEY_UNKNOWN_LANGUAGE_FILTER, true)
    }

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) = preferences.edit().putBoolean(when (language) {
        is Language.Known -> KEY_LANGUAGE_FILTER + language.id
        is Language.Unknown -> KEY_UNKNOWN_LANGUAGE_FILTER
    }, isEnabled).apply()

    private sealed class PreferenceFieldDelegate<T>(protected val key: String, protected val defaultValue: T) : ReadWriteProperty<PreferenceStorageManager, T> {

        class Boolean(key: String, defaultValue: kotlin.Boolean = false) : PreferenceFieldDelegate<kotlin.Boolean>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getBoolean(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Boolean) = thisRef.preferences.edit().putBoolean(key, value).apply()
        }

        class Long(key: String, defaultValue: kotlin.Long = 0) : PreferenceFieldDelegate<kotlin.Long>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getLong(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Long) = thisRef.preferences.edit().putLong(key, value).apply()
        }
    }

    private companion object {
        private const val KEY_NAVIGATION_ITEM = "navigation_item"
        private const val VALUE_LIBRARY = "library"
        private const val VALUE_COLLECTIONS = "collections"
        private const val VALUE_HISTORY = "history"
        private const val VALUE_SETTINGS = "settings"
        private const val VALUE_PLAYLIST = "playlist_"
        private const val VALUE_MANAGE_PLAYLISTS = "manage_playlists"
        private const val VALUE_MANAGE_DOWNLOADS = "manage_downloads"
        private const val KEY_LANGUAGE_FILTER = "language_filter_"
        private const val KEY_UNKNOWN_LANGUAGE_FILTER = "unknown_language_filter"
    }
}