package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.HomeViewModel


/**
 * Wrapper for locally storing simple key-value pairs.
 */
class StorageManager(context: Context, private val gson: Gson) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /**
     * The last selected item from the home screen's side navigation.
     */
    var navigationItem: HomeViewModel.NavigationItem
        get() {
            sharedPreferences.getString(KEY_NAVIGATION_ITEM, VALUE_LIBRARY).let {
                return when (it) {
                    VALUE_LIBRARY -> HomeViewModel.NavigationItem.Library
                    VALUE_SETTINGS -> HomeViewModel.NavigationItem.Settings
                    else -> HomeViewModel.NavigationItem.Playlist(it.removePrefix(VALUE_PLAYLIST))
                }
            }
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_NAVIGATION_ITEM, when (value) {
                HomeViewModel.NavigationItem.Library -> VALUE_LIBRARY
                HomeViewModel.NavigationItem.Settings -> VALUE_SETTINGS
                is HomeViewModel.NavigationItem.Playlist -> VALUE_PLAYLIST + value.id
            }).apply()
        }

    /**
     * The timestamp of the most recent update that helps to determine how old is the local cache.
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
     * The cached list of items from the cloud.
     *
     * TODO: This shouldn't be stored in Shared Preferences, replace it with a Room-based implementation.
     */
    var cloudCache: List<SongInfo>
        get() = try {
            gson.fromJson(sharedPreferences.getString(KEY_CACHE, "[]"), object : TypeToken<List<SongInfo>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_CACHE, gson.toJson(value)).apply()
        }

    /**
     * The list of downloads songs.
     */
    var downloads: List<DownloadedSong>
        get() = try {
            gson.fromJson(sharedPreferences.getString(KEY_DOWNLOADED_SONGS, "[]"), object : TypeToken<List<DownloadedSong>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_DOWNLOADED_SONGS, gson.toJson(value)).apply()
        }

    /**
     * The list of song ID-s that the user marked as favorites.
     */
    var favorites: List<String>
        get() = try {
            gson.fromJson(sharedPreferences.getString(FAVORITES, "[]"), object : TypeToken<List<String>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(FAVORITES, gson.toJson(value)).apply()
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
        private const val VALUE_SETTINGS = "settings"
        private const val VALUE_PLAYLIST = "playlist_"
        private const val KEY_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
        private const val KEY_IS_SORTED_BY_TITLE = "is_sorted_by_title"
        private const val KEY_SHOULD_SHOW_DOWNLOADED_ONLY = "should_show_downloaded_only"
        private const val KEY_SHOULD_HIDE_EXPLICIT = "should_hide_explicit"
        private const val KEY_CACHE = "cache"
        private const val KEY_DOWNLOADED_SONGS = "downloaded_songs"
        private const val KEY_LANGUAGE_FILTER = "language_filter_"
        private const val KEY_UNKNOWN_LANGUAGE_FILTER = "unknown_language_filter_"
        private const val FAVORITES = "favorites" //TODO: Generalize to support multiple lists.
    }
}