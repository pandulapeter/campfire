package com.pandulapeter.campfire.feature.home

import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val preferenceStorageManager: PreferenceStorageManager) : CampfireViewModel() {
    var navigationItem: NavigationItem = preferenceStorageManager.navigationItem
        set(value) {
            if (field != value) {
                field = value
                preferenceStorageManager.navigationItem = value
            }
        }

    /**
     * Marks the possible screens the user can reach using the side navigation on the home screen.
     */
    sealed class NavigationItem {
        object Library : NavigationItem()
        object Settings : NavigationItem()
        class Playlist(val id: String) : NavigationItem()
    }
}