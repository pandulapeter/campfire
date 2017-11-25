package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.pandulapeter.campfire.feature.home.NavigationItem

/**
 * Wrapper for locally storing simple key-value pairs.
 */
class StorageManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

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

    companion object {
        private const val LAST_SELECTED_NAVIGATION_ITEM = "last_selected_navigation_item"
    }
}