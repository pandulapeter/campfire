package com.pandulapeter.campfire.feature.home

import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val storageManager: StorageManager) : CampfireViewModel() {
    var navigationItem: NavigationItem = storageManager.navigationItem
        set(value) {
            if (field != value) {
                field = value
                storageManager.navigationItem = value
            }
        }

    /**
     * Marks the possible screens the user can reach using the side navigation on the home screen.
     */
    sealed class NavigationItem {
        object LIBRARY : NavigationItem()
        object SETTINGS : NavigationItem()
        class PLAYLIST(val id: String) : NavigationItem()
    }
}