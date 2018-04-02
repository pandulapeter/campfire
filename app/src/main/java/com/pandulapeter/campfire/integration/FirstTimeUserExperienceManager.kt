package com.pandulapeter.campfire.integration

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase

class FirstTimeUserExperienceManager(private val preferenceDatabase: PreferenceDatabase) {
//    var shouldShowHistoryHint by Delegates.observable(preferenceStorageManager.shouldShowHistoryHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
//        if (old != new) {
//            preferenceStorageManager.shouldShowHistoryHint = new
//        }
//    }
//    var shouldShowPlaylistHint by Delegates.observable(preferenceStorageManager.shouldShowPlaylistHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
//        if (old != new) {
//            preferenceStorageManager.shouldShowPlaylistHint = new
//        }
//    }
//    var shouldShowManagePlaylistsHint by Delegates.observable(preferenceStorageManager.shouldShowManagePlaylistsHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
//        if (old != new) {
//            preferenceStorageManager.shouldShowManagePlaylistsHint = new
//        }
//    }
//    var shouldShowManageDownloadsHint by Delegates.observable(preferenceStorageManager.shouldShowManageDownloadsHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
//        if (old != new) {
//            preferenceStorageManager.shouldShowManageDownloadsHint = new
//        }
//    }
//    var shouldShowDetailSwipeHint by Delegates.observable(preferenceStorageManager.shouldShowDetailSwipeHint) { _: KProperty<*>, old: Boolean, new: Boolean ->
//        if (old != new) {
//            preferenceStorageManager.shouldShowDetailSwipeHint = new
//        }
//    }

    fun resetAll() {
//        shouldShowHistoryHint = true
//        shouldShowPlaylistHint = true
//        shouldShowManagePlaylistsHint = true
//        shouldShowManageDownloadsHint = true
//        shouldShowDetailSwipeHint = true
    }
}