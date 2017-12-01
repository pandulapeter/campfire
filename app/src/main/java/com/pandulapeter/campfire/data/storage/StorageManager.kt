package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
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
                    VALUE_LIBRARY -> HomeViewModel.NavigationItem.LIBRARY
                    VALUE_SETTINGS -> HomeViewModel.NavigationItem.SETTINGS
                    else -> HomeViewModel.NavigationItem.PLAYLIST(it.removePrefix(VALUE_PLAYLIST))
                }
            }
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_NAVIGATION_ITEM, when (value) {
                HomeViewModel.NavigationItem.LIBRARY -> VALUE_LIBRARY
                HomeViewModel.NavigationItem.SETTINGS -> VALUE_SETTINGS
                is HomeViewModel.NavigationItem.PLAYLIST -> VALUE_PLAYLIST + value.id
            }).apply()
        }

    /**
     * The timestamp of the most recent update that helps to determine how old is the local cache.
     */
    var lastCacheUpdateTimestamp: Long
        get() = sharedPreferences.getLong(LAST_CACHE_UPDATE_TIMESTAMP, 0)
        set(value) {
            sharedPreferences.edit().putLong(LAST_CACHE_UPDATE_TIMESTAMP, value).apply()
        }

    /**
     * Whether or not the user's last saved preference was to sort the songs by title.
     */
    var isSortedByTitle: Boolean
        get() = sharedPreferences.getBoolean(IS_SORTED_BY_TITLE, true)
        set(value) {
            sharedPreferences.edit().putBoolean(IS_SORTED_BY_TITLE, value).apply()
        }

    /**
     * Whether or not to hide songs from the cloud.
     */
    var shouldShowDownloadedOnly: Boolean
        get() = sharedPreferences.getBoolean(SHOULD_SHOW_DOWNLOADED_ONLY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHOULD_SHOW_DOWNLOADED_ONLY, value).apply()
        }

    /**
     * Whether or not explicit songs should be filtered out.
     */
    var shouldHideExplicit: Boolean
        get() = sharedPreferences.getBoolean(SHOULD_HIDE_EXPLICIT, true)
        set(value) {
            sharedPreferences.edit().putBoolean(SHOULD_HIDE_EXPLICIT, value).apply()
        }

    /**
     * The cached list of items from the cloud.
     *
     * TODO: This shouldn't be stored in Shared Preferences, replace it with a Room-based implementation.
     */
    var cloudCache: List<SongInfo>
        get() = try {
            gson.fromJson(sharedPreferences.getString(CLOUD_CACHE, "[]"), object : TypeToken<List<SongInfo>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(CLOUD_CACHE, gson.toJson(value)).apply()
        }

    /**
     * The list of downloads songs.
     */
    var downloads: List<String>
        get() = try {
            gson.fromJson(sharedPreferences.getString(DOWNLOADS, "[]"), object : TypeToken<List<String>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(DOWNLOADS, gson.toJson(value)).apply()
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

    companion object {
        private const val KEY_NAVIGATION_ITEM = "navigation_item"
        private const val VALUE_LIBRARY = "library"
        private const val VALUE_SETTINGS = "settings"
        private const val VALUE_PLAYLIST = "playlist_"
        private const val LAST_CACHE_UPDATE_TIMESTAMP = "last_cache_update_timestamp"
        private const val IS_SORTED_BY_TITLE = "is_sorted_by_title"
        private const val SHOULD_SHOW_DOWNLOADED_ONLY = "should_show_downloaded_only"
        private const val SHOULD_HIDE_EXPLICIT = "should_hide_explicit"
        private const val CLOUD_CACHE = "cloud_cache"
        private const val DOWNLOADS = "downloads"
        private const val FAVORITES = "favorites"
    }
}