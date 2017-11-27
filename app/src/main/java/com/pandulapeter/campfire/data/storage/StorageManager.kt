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
     * The last selected item from the home screen's bottom navigation bar.
     */
    var lastSelectedNavigationItem: HomeViewModel.NavigationItem
        get() = when (sharedPreferences.getInt(LAST_SELECTED_NAVIGATION_ITEM, 0)) {
            1 -> HomeViewModel.NavigationItem.DOWNLOADS
            2 -> HomeViewModel.NavigationItem.FAVORITES
            else -> HomeViewModel.NavigationItem.CLOUD
        }
        set(value) {
            sharedPreferences.edit().putInt(LAST_SELECTED_NAVIGATION_ITEM, when (value) {
                HomeViewModel.NavigationItem.CLOUD -> 0
                HomeViewModel.NavigationItem.DOWNLOADS -> 1
                HomeViewModel.NavigationItem.FAVORITES -> 2
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
        private const val LAST_SELECTED_NAVIGATION_ITEM = "last_selected_navigation_item"
        private const val LAST_CACHE_UPDATE_TIMESTAMP = "last_cache_update_timestamp"
        private const val IS_SORTED_BY_TITLE = "is_sorted_by_title"
        private const val SHOULD_HIDE_EXPLICIT = "should_hide_explicit"
        private const val CLOUD_CACHE = "cloud_cache"
        private const val DOWNLOADS = "downloads"
        private const val FAVORITES = "favorites"
    }
}