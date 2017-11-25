package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.NavigationItem


/**
 * Wrapper for locally storing simple key-value pairs.
 */
class StorageManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /**
     * The last selected item from the home screen's bottom navigation bar.
     */
    var lastSelectedNavigationItem: NavigationItem
        get() = when (sharedPreferences.getInt(LAST_SELECTED_NAVIGATION_ITEM, 0)) {
            1 -> NavigationItem.DOWNLOADED
            2 -> NavigationItem.FAVORITES
            else -> NavigationItem.LIBRARY
        }
        set(value) {
            sharedPreferences.edit().putInt(LAST_SELECTED_NAVIGATION_ITEM, when (value) {
                NavigationItem.LIBRARY -> 0
                NavigationItem.DOWNLOADED -> 1
                NavigationItem.FAVORITES -> 2
            }).apply()
        }

    /**
     * The timestamp of the most recent update that helps to determine how old is the local cache.
     */
    var lastLibraryUpdate: Long
        get() = sharedPreferences.getLong(LAST_LIBRARY_UPDATE, 0)
        set(value) {
            sharedPreferences.edit().putLong(LAST_LIBRARY_UPDATE, value).apply()
        }

    /**
     * The cached list of library items.
     *
     * TODO: This shouldn't be stored in Shared Preferences, replace it with a Room-based implementation.
     */
    var library: List<SongInfo>
        get() = try {
            Gson().fromJson(sharedPreferences.getString(LIBRARY, "[]"), object : TypeToken<List<SongInfo>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(LIBRARY, Gson().toJson(value)).apply()
        }

    companion object {
        private const val LAST_SELECTED_NAVIGATION_ITEM = "last_selected_navigation_item"
        private const val LAST_LIBRARY_UPDATE = "last_library_update"
        private const val LIBRARY = "library"
    }
}