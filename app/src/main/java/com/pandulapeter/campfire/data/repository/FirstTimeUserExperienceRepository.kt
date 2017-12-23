package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of the first time user experience state.
 */
class FirstTimeUserExperienceRepository(
    private val preferenceStorageManager: PreferenceStorageManager) {
    var shouldShowHistoryHint by Delegates.observable(preferenceStorageManager.shouldShowHistoryHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldShowHistoryHint = new
        }
    }
    var shouldShowPlaylistHint by Delegates.observable(preferenceStorageManager.shouldShowPlaylistHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldShowPlaylistHint = new
        }
    }
}