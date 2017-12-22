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
class UserPreferenceRepository(
    private val preferenceStorageManager: PreferenceStorageManager) : Repository() {
    var navigationItem by Delegates.observable(preferenceStorageManager.navigationItem) { _: KProperty<*>, old: HomeViewModel.NavigationItem, new: HomeViewModel.NavigationItem ->
        if (old != new) {
            notifySubscribers(UpdateType.NavigationItemUpdated(new))
            preferenceStorageManager.navigationItem = new
        }
    }
    var isSortedByTitle by Delegates.observable(preferenceStorageManager.isSortedByTitle) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.IsSortedByTitleUpdated(new))
            preferenceStorageManager.isSortedByTitle = new
        }
    }
    var shouldShowDownloadedOnly by Delegates.observable(preferenceStorageManager.shouldShowDownloadedOnly) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldShowDownloadedOnlyUpdated(new))
            preferenceStorageManager.shouldShowDownloadedOnly = new
        }
    }
    var shouldHideExplicit by Delegates.observable(preferenceStorageManager.shouldHideExplicit) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldHideExplicitUpdated(new))
            preferenceStorageManager.shouldHideExplicit = new
        }
    }
    var shouldHideWorkInProgress by Delegates.observable(preferenceStorageManager.shouldHideWorkInProgress) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldHideWorkInProgressUpdated(new))
            preferenceStorageManager.shouldHideWorkInProgress = new
        }
    }
    var shouldShowSongCount by Delegates.observable(preferenceStorageManager.shouldShowSongCount) { _: KProperty<*>, old: Boolean, new: Boolean ->
        if (old != new) {
            notifySubscribers(UpdateType.ShouldShowSongCountUpdated(new))
            preferenceStorageManager.shouldShowSongCount = new
        }
    }
    var searchQuery by Delegates.observable("") { _: KProperty<*>, old: String, new: String ->
        if (!TextUtils.equals(old, new)) {
            notifySubscribers(UpdateType.SearchQueryUpdated(new))
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        super.subscribe(subscriber)
        subscriber.onUpdate(UpdateType.NavigationItemUpdated(navigationItem))
        subscriber.onUpdate(UpdateType.IsSortedByTitleUpdated(isSortedByTitle))
        subscriber.onUpdate(UpdateType.ShouldShowDownloadedOnlyUpdated(shouldShowDownloadedOnly))
        subscriber.onUpdate(UpdateType.ShouldHideExplicitUpdated(shouldHideExplicit))
        subscriber.onUpdate(UpdateType.ShouldHideWorkInProgressUpdated(shouldHideWorkInProgress))
        subscriber.onUpdate(UpdateType.ShouldShowSongCountUpdated(shouldShowSongCount))
        subscriber.onUpdate(UpdateType.SearchQueryUpdated(searchQuery))
    }
}