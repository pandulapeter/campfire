package com.pandulapeter.campfire.feature.home.settings

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [SettingsFragment].
 */
class SettingsViewModel(analyticsManager: AnalyticsManager) : HomeChildViewModel(analyticsManager) {
    val shouldAllowToolbarScrolling = ObservableBoolean()
}