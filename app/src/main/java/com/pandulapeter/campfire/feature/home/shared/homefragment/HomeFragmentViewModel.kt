package com.pandulapeter.campfire.feature.home.shared.homefragment

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(private val homeCallbacks: HomeFragment.HomeCallbacks?) : CampfireViewModel() {

    fun showMenu() {
        homeCallbacks?.showMenu()
    }
}