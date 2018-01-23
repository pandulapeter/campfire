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
    var shouldShowExplicit by PreferenceFieldDelegate.Boolean("should_show_explicit", false)
    var shouldShowWorkInProgress by PreferenceFieldDelegate.Boolean("should_show_work_in_progress", BuildConfig.DEBUG)
    var shouldShowChords by PreferenceFieldDelegate.Boolean("should_show_chords", true)
    var shouldUseGermanNotation by PreferenceFieldDelegate.Boolean("should_use_german_notation", false) //TODO: Enable by default in some countries.
    var shouldShowHistoryHint by PreferenceFieldDelegate.Boolean("should_show_history_hint", true)
    var shouldShowPlaylistHint by PreferenceFieldDelegate.Boolean("should_show_playlist_hint", true)
    var shouldShowManageDownloadsHint by PreferenceFieldDelegate.Boolean("should_show_manage_downloads_hint", true)
    var shouldShowDetailSwipeHint by PreferenceFieldDelegate.Boolean("should_show_detail_swipe_hint", true)
    var homeNavigationItem: HomeViewModel.HomeNavigationItem
        get() = HomeViewModel.HomeNavigationItem.fromStringValue(preferences.getString(KEY_NAVIGATION_ITEM, null))
        set(value) = preferences.edit().putString(KEY_NAVIGATION_ITEM, value.stringValue).apply()

    fun isLanguageFilterEnabled(language: Language) = when (language) {
        is Language.Known -> preferences.getBoolean(KEY_LANGUAGE_FILTER + language.id, true)
        Language.Unknown -> preferences.getBoolean(KEY_UNKNOWN_LANGUAGE_FILTER, true)
    }

    fun setLanguageFilterEnabled(language: Language, isEnabled: Boolean) = preferences.edit().putBoolean(
        when (language) {
            is Language.Known -> KEY_LANGUAGE_FILTER + language.id
            Language.Unknown -> KEY_UNKNOWN_LANGUAGE_FILTER
        }, isEnabled
    ).apply()

    private sealed class PreferenceFieldDelegate<T>(protected val key: String, protected val defaultValue: T) : ReadWriteProperty<PreferenceStorageManager, T> {

        class Boolean(key: String, defaultValue: kotlin.Boolean = false) : PreferenceFieldDelegate<kotlin.Boolean>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getBoolean(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Boolean) =
                thisRef.preferences.edit().putBoolean(key, value).apply()
        }

        class Long(key: String, defaultValue: kotlin.Long = 0) : PreferenceFieldDelegate<kotlin.Long>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getLong(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Long) =
                thisRef.preferences.edit().putLong(key, value).apply()
        }
    }

    private companion object {
        private const val KEY_NAVIGATION_ITEM = "navigation_item"
        private const val KEY_LANGUAGE_FILTER = "language_filter_"
        private const val KEY_UNKNOWN_LANGUAGE_FILTER = "unknown_language_filter"
    }
}