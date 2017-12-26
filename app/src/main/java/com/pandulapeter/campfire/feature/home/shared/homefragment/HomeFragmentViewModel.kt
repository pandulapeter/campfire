package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel : CampfireViewModel() {
    val shouldShowMenu = ObservableBoolean()

    fun showMenu() = shouldShowMenu.set(true)
}