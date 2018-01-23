package com.pandulapeter.campfire.feature.home.settings

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.toNoteName

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
    val shouldUseGermanNotationDescription = ObservableField(generateNotationExample(shouldUseGermanNotation.get()))

    init {
        shouldShowChords.onPropertyChanged { userPreferenceRepository.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged {
            userPreferenceRepository.shouldUseGermanNotation = it
            shouldUseGermanNotationDescription.set(generateNotationExample(it))
        }
    }

    private fun generateNotationExample(shouldUseGermanNotation: Boolean) =
        0.toNoteName(shouldUseGermanNotation) + ", " +
                2.toNoteName(shouldUseGermanNotation) + ", " +
                4.toNoteName(shouldUseGermanNotation) + ", " +
                5.toNoteName(shouldUseGermanNotation) + ", " +
                7.toNoteName(shouldUseGermanNotation) + ", " +
                9.toNoteName(shouldUseGermanNotation) + ", " +
                11.toNoteName(shouldUseGermanNotation)
}