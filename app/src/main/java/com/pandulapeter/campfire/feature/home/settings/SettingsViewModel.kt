package com.pandulapeter.campfire.feature.home.settings

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [SettingsFragment].
 */
class SettingsViewModel(
    analyticsManager: AnalyticsManager,
    private val userPreferenceRepository: UserPreferenceRepository
) : HomeChildViewModel(analyticsManager) {
    val shouldAllowToolbarScrolling = ObservableBoolean()
    val shouldShowChords = ObservableBoolean(userPreferenceRepository.shouldShowChords)
    val shouldUseGermanNotation = ObservableBoolean(userPreferenceRepository.shouldUseGermanNotation)

    init {
        shouldShowChords.onPropertyChanged { userPreferenceRepository.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged { userPreferenceRepository.shouldUseGermanNotation = it }
    }
}