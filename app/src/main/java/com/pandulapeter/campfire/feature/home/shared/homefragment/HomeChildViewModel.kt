package com.pandulapeter.campfire.feature.home.shared.homefragment

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeChildFragment].
 */
abstract class HomeChildViewModel(analyticsManager: AnalyticsManager) : CampfireViewModel(analyticsManager) {
    val shouldShowMenu = ObservableBoolean()
    val shouldPlayReturnAnimation = ObservableBoolean()

    fun showMenu() = shouldShowMenu.set(true)
}