package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.storage.PreferenceStorageManager

/**
 * Wraps caching and updating of user preferences.
 */
class UserPreferenceRepository(
    private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    var isSortedByTitle = preferenceStorageManager.isSortedByTitle
        set(value) {
            if (field != value) {
                field = value
                preferenceStorageManager.isSortedByTitle = value
                notifySubscribers()
            }
        }
    var shouldShowDownloadedOnly = preferenceStorageManager.shouldShowDownloadedOnly
        set(value) {
            if (field != value) {
                field = value
                preferenceStorageManager.shouldShowDownloadedOnly = value
                notifySubscribers()
            }
        }
    var shouldHideExplicit = preferenceStorageManager.shouldHideExplicit
        set(value) {
            if (field != value) {
                field = value
                preferenceStorageManager.shouldHideExplicit = value
                notifySubscribers()
            }
        }
    var shouldHideWorkInProgress = preferenceStorageManager.shouldHideWorkInProgress
        set(value) {
            if (field != value) {
                field = value
                preferenceStorageManager.shouldHideWorkInProgress = value
                notifySubscribers()
            }
        }
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                notifySubscribers()
            }
        }
}