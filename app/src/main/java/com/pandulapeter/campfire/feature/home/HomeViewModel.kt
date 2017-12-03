package com.pandulapeter.campfire.feature.home

import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val userPreferenceRepository: UserPreferenceRepository) : CampfireViewModel() {
    var navigationItem: NavigationItem = userPreferenceRepository.navigationItem
        set(value) {
            field = value
            userPreferenceRepository.navigationItem = value
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