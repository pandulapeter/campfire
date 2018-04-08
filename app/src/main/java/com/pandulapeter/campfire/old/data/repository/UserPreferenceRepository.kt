package com.pandulapeter.campfire.old.data.repository

import com.pandulapeter.campfire.old.data.repository.shared.Repository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.old.feature.home.HomeViewModel
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of user preferences.
 */
class UserPreferenceRepository(private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    var shouldUseDarkTheme by Delegates.observable(preferenceStorageManager.shouldUseDarkTheme) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldUseDarkTheme = new
        }
    }
    var navigationItem by Delegates.observable(preferenceStorageManager.homeNavigationItem) { _: KProperty<*>, old: HomeViewModel.HomeNavigationItem, new: HomeViewModel.HomeNavigationItem ->
        if (old != new) {
            notifySubscribers(UpdateType.NavigationItemUpdated)
            preferenceStorageManager.homeNavigationItem = new
        }
    }
    var shouldShowChords by Delegates.observable(preferenceStorageManager.shouldShowChords) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldShowChords = new
        }
    }
    var shouldEnableAutoScroll by Delegates.observable(preferenceStorageManager.shouldEnableAutoScroll) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldEnableAutoScroll = new
        }
    }
    var shouldUseGermanNotation by Delegates.observable(preferenceStorageManager.shouldUseGermanNotation) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            preferenceStorageManager.shouldUseGermanNotation = new
        }
    }

    fun getSongAutoScrollSpeed(songId: String) = preferenceStorageManager.getSongAutoScrollSpeed(songId)

    fun setSongAutoScrollSpeed(songId: String, autoScrollSpeed: Int) = preferenceStorageManager.setSongAutoScrollSpeed(songId, autoScrollSpeed)

    fun getSongTransposition(songId: String) = preferenceStorageManager.getSongTransposition(songId)

    fun setSongTransposition(songId: String, transposition: Int) = preferenceStorageManager.setSongTransposition(songId, transposition)

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.NavigationItemUpdated)
        subscriber.onUpdate(UpdateType.SortingModeUpdated)
        subscriber.onUpdate(UpdateType.ShouldShowDownloadedOnlyUpdated)
        subscriber.onUpdate(UpdateType.ShouldHideExplicitUpdated)
        subscriber.onUpdate(UpdateType.ShouldHideWorkInProgressUpdated)
        subscriber.onUpdate(UpdateType.SearchQueryUpdated)
    }
}