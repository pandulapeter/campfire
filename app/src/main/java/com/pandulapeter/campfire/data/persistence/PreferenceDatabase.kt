package com.pandulapeter.campfire.data.persistence

import android.content.Context
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.feature.home.library.LibraryViewModel
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PreferenceDatabase(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val locale by lazy { Locale.getDefault().isO3Country.toUpperCase() }

    // Library updating
    var lastUpdateTimestamp by PreferenceFieldDelegate.Long("lastUpdateTimestamp")

    // Library filters
    var shouldSearchInArtists by PreferenceFieldDelegate.Boolean("shouldSearchInArtists", true)
    var shouldSearchInTitles by PreferenceFieldDelegate.Boolean("shouldSearchInTitles", true)
    var shouldShowDownloadedOnly by PreferenceFieldDelegate.Boolean("shouldShowDownloadedOnly", false)
    var shouldShowExplicitLibrary by PreferenceFieldDelegate.Boolean("shouldShowExplicitLibrary", false)
    var sortingMode by PreferenceFieldDelegate.Int("sortingMode", LibraryViewModel.SortingMode.TITLE.intValue)
    var disabledLibraryLanguageFilters by PreferenceFieldDelegate.StringSet("disabledLibraryLanguageFilters", getDefaultLanguageFilters())

    // Collections filters
    var shouldSortByPopularity by PreferenceFieldDelegate.Boolean("shouldSortByPopularity", false)
    var shouldShowSavedOnly by PreferenceFieldDelegate.Boolean("shouldShowSavedOnly", false)
    var shouldShowExplicitCollections by PreferenceFieldDelegate.Boolean("shouldShowExplicitCollections", false)
    var disabledCollectionsLanguageFilters by PreferenceFieldDelegate.StringSet("disabledCollectionsLanguageFilters", getDefaultLanguageFilters())

    // Preferences
    var shouldShowChords by PreferenceFieldDelegate.Boolean("shouldShowChords", true)
    var shouldUseGermanNotation by PreferenceFieldDelegate.Boolean("shouldUseGermanNotation", shouldEnableGermanNotationByDefault())
    var shouldUseDarkTheme by PreferenceFieldDelegate.Boolean("shouldUseDarkTheme", true)
    var shouldShowExitConfirmation by PreferenceFieldDelegate.Boolean("shouldShowExitConfirmation", true)
    var shouldShowPrivacyPolicy by PreferenceFieldDelegate.Boolean("shouldShowPrivacyPolicy", true)
    var shouldShareUsageData by PreferenceFieldDelegate.Boolean("shouldShareUsageData", false)
    var playlistHistory by PreferenceFieldDelegate.StringSet("playlistHistory", setOf())
    var lastScreen by PreferenceFieldDelegate.String("lastScreen", "")

    // First time user experience
    var ftuxHistoryCompleted by PreferenceFieldDelegate.Boolean("ftuxHistoryCompleted", false)
    var ftuxPlaylistSwipeCompleted by PreferenceFieldDelegate.Boolean("ftuxPlaylistSwipeCompleted", false)
    var ftuxPlaylistDragCompleted by PreferenceFieldDelegate.Boolean("ftuxPlaylistDragCompleted", false)
    var ftuxManagePlaylistsSwipeCompleted by PreferenceFieldDelegate.Boolean("ftuxManagePlaylistsSwipeCompleted", false)
    var ftuxManagePlaylistsDragCompleted by PreferenceFieldDelegate.Boolean("ftuxManagePlaylistsDragCompleted", false)
    var ftuxManageDownloadsCompleted by PreferenceFieldDelegate.Boolean("ftuxManageDownloadsCompleted", false)

    private fun shouldEnableGermanNotationByDefault() = when (locale) {
        "AUT", "CZE", "DEU", "SWE", "DNK", "EST", "FIN", "HUN", "LVA", "NOR", "POL", "SRB", "SVK" -> true
        else -> false
    }

    private fun getDefaultLanguageFilters() = mutableSetOf<String>().apply {
        if (locale != "HUN" && locale != "ROU") {
            add(Language.Known.Hungarian.id)
        }
        if (locale != "ROU") {
            add(Language.Known.Romanian.id)
        }
    }

    private sealed class PreferenceFieldDelegate<T>(protected val key: kotlin.String, protected val defaultValue: T) : ReadWriteProperty<PreferenceDatabase, T> {

        class Boolean(key: kotlin.String, defaultValue: kotlin.Boolean = false) : PreferenceFieldDelegate<kotlin.Boolean>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getBoolean(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Boolean) =
                thisRef.preferences.edit().putBoolean(key, value).apply()
        }

        class Int(key: kotlin.String, defaultValue: kotlin.Int = 0) : PreferenceFieldDelegate<kotlin.Int>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getInt(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Int) =
                thisRef.preferences.edit().putInt(key, value).apply()
        }

        class Long(key: kotlin.String, defaultValue: kotlin.Long = 0) : PreferenceFieldDelegate<kotlin.Long>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getLong(key, defaultValue)

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.Long) =
                thisRef.preferences.edit().putLong(key, value).apply()
        }

        class String(key: kotlin.String, defaultValue: kotlin.String = "") : PreferenceFieldDelegate<kotlin.String>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>) = thisRef.preferences.getString(key, defaultValue) ?: defaultValue

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: kotlin.String) =
                thisRef.preferences.edit().putString(key, value).apply()
        }

        class StringSet(key: kotlin.String, defaultValue: Set<kotlin.String>) : PreferenceFieldDelegate<Set<kotlin.String>>(key, defaultValue) {

            override fun getValue(thisRef: PreferenceDatabase, property: KProperty<*>): Set<kotlin.String> = thisRef.preferences.getStringSet(key, defaultValue) ?: setOf()

            override fun setValue(thisRef: PreferenceDatabase, property: KProperty<*>, value: Set<kotlin.String>) =
                thisRef.preferences.edit().putStringSet(key, value).apply()
        }
    }
}