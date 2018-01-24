package com.pandulapeter.campfire.data.repository

import android.text.TextUtils
import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Wraps caching and updating of user preferences.
 */
class UserPreferenceRepository(private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    var navigationItem by Delegates.observable(preferenceStorageManager.homeNavigationItem) { _: KProperty<*>, old: HomeViewModel.HomeNavigationItem, new: HomeViewModel.HomeNavigationItem ->
        if (old != new) {
            notifySubscribers(UpdateType.NavigationItemUpdated)
            preferenceStorageManager.homeNavigationItem = new
        }
    }
    var isSortedByTitle by Delegates.observable(preferenceStorageManager.isSortedByTitle) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.IsSortedByTitleUpdated)
            preferenceStorageManager.isSortedByTitle = new
        }
    }
    var shouldShowDownloadedOnly by Delegates.observable(preferenceStorageManager.shouldShowDownloadedOnly) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldShowDownloadedOnlyUpdated)
            preferenceStorageManager.shouldShowDownloadedOnly = new
        }
    }
    var shouldShowExplicit by Delegates.observable(preferenceStorageManager.shouldShowExplicit) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldHideExplicitUpdated)
            preferenceStorageManager.shouldShowExplicit = new
        }
    }
    var shouldShowWorkInProgress by Delegates.observable(preferenceStorageManager.shouldShowWorkInProgress) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldHideWorkInProgressUpdated)
            preferenceStorageManager.shouldShowWorkInProgress = new
        }
    }
    var shouldShowChords by Delegates.observable(preferenceStorageManager.shouldShowChords) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldShowChords(new))
            preferenceStorageManager.shouldShowChords = new
        }
    }
    var shouldUseGermanNotation by Delegates.observable(preferenceStorageManager.shouldUseGermanNotation) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldShowChords(new))
            preferenceStorageManager.shouldUseGermanNotation = new
        }
    }
    var searchQuery by Delegates.observable("") { _: KProperty<*>, old: String, new: String ->
        if (!TextUtils.equals(old, new)) {
            notifySubscribers(UpdateType.SearchQueryUpdated)
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.NavigationItemUpdated)
        subscriber.onUpdate(UpdateType.IsSortedByTitleUpdated)
        subscriber.onUpdate(UpdateType.ShouldShowDownloadedOnlyUpdated)
        subscriber.onUpdate(UpdateType.ShouldHideExplicitUpdated)
        subscriber.onUpdate(UpdateType.ShouldHideWorkInProgressUpdated)
        subscriber.onUpdate(UpdateType.SearchQueryUpdated)
    }
}