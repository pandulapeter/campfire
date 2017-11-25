package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager

/**
 * Wrapper for locally storing simple key-value pairs.
 */
class StorageManager(context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
}