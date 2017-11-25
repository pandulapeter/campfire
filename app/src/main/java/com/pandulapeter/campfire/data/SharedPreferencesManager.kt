package com.pandulapeter.campfire.data

import android.content.Context
import android.preference.PreferenceManager

/**
 * Wrapper for locally storing simple key-value pairs.
 */
class SharedPreferencesManager(context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
}