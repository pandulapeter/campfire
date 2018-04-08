package com.pandulapeter.campfire.old.data.storage

import android.content.Context
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.old.feature.home.HomeViewModel
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Wrapper for locally storing simple key-value pairs. Used for saving user preferences.
 */
class PreferenceStorageManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("preference_storage", Context.MODE_PRIVATE)
    var lastUpdateTimestamp by PreferenceFieldDelegate.Long("last_update_timestamp")
    var shouldUseDarkTheme by PreferenceFieldDelegate.Boolean("should_use_dark_theme", false)
    var shouldShowDownloadedOnly by PreferenceFieldDelegate.Boolean("should_show_downloaded_only")
    var shouldShowExplicit by PreferenceFieldDelegate.Boolean("should_show_explicit", false)
    var shouldShowWorkInProgress by PreferenceFieldDelegate.Boolean("should_show_work_in_progress", BuildConfig.DEBUG)
    var shouldShowChords by PreferenceFieldDelegate.Boolean("should_show_chords", true)
    var shouldEnableAutoScroll by PreferenceFieldDelegate.Boolean("should_enable_auto_scroll", true)
    var shouldShowExitConfirmation by PreferenceFieldDelegate.Boolean("should_show_exit_confirmation", true)
    var shouldUseGermanNotation by PreferenceFieldDelegate.Boolean("should_use_german_notation", shouldEnableGermanNotationByDefault())
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

    fun getSongAutoScrollSpeed(songId: String) = Math.max(0, Math.min(14, preferences.getInt(KEY_SONG_AUTO_SCROLL_SPEED + songId, 4)))

    fun setSongAutoScrollSpeed(songId: String, autoScrollSpeed: Int) =
        preferences.edit().putInt(KEY_SONG_AUTO_SCROLL_SPEED + songId, Math.max(0, Math.min(14, autoScrollSpeed))).apply()

    fun getSongTransposition(songId: String) = Math.max(-6, Math.min(6, preferences.getInt(KEY_SONG_TRANSPOSITION + songId, 0)))

    fun setSongTransposition(songId: String, transposition: Int) =
        preferences.edit().putInt(KEY_SONG_TRANSPOSITION + songId, Math.max(-6, Math.min(6, transposition))).apply()

    private fun shouldEnableGermanNotationByDefault() = when (Locale.getDefault().isO3Country.toUpperCase()) {
        "AUT", "CZE", "DEU", "SWE", "DNK", "EST", "FIN", "HUN", "LVA", "NOR", "POL", "SRB", "SVK" -> true
        else -> false
    }

    private sealed class PreferenceFieldDelegate<T>(protected val key: String, protected val defaultValue: T) : ReadWriteProperty<PreferenceStorageManager, T> {

        class Boolean(key: String, defaultValue: kotlin.Boolean = false) : PreferenceFieldDelegate<kotlin.Boolean>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getBoolean(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Boolean) =
                thisRef.preferences.edit().putBoolean(key, value).apply()
        }

        class Int(key: String, defaultValue: kotlin.Int = 0) : PreferenceFieldDelegate<kotlin.Int>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceStorageManager, property: KProperty<*>) = thisRef.preferences.getInt(key, defaultValue)

            override fun setValue(thisRef: PreferenceStorageManager, property: KProperty<*>, value: kotlin.Int) =
                thisRef.preferences.edit().putInt(key, value).apply()
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
        private const val KEY_SONG_AUTO_SCROLL_SPEED = "song_auto_scroll_speed_"
        private const val KEY_SONG_TRANSPOSITION = "song_transposition_"
    }
}