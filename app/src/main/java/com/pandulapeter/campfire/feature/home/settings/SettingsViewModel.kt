package com.pandulapeter.campfire.feature.home.settings

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.Note
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
    val shouldUseGermanNotationDescription = ObservableField(generateNotationExample(shouldUseGermanNotation.get()))

    init {
        shouldShowChords.onPropertyChanged { userPreferenceRepository.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged {
            userPreferenceRepository.shouldUseGermanNotation = it
            shouldUseGermanNotationDescription.set(generateNotationExample(it))
        }
    }

    private fun generateNotationExample(shouldUseGermanNotation: Boolean) =
        Note.C.getName(shouldUseGermanNotation) + ", " +
                Note.D.getName(shouldUseGermanNotation) + ", " +
                Note.E.getName(shouldUseGermanNotation) + ", " +
                Note.F.getName(shouldUseGermanNotation) + ", " +
                Note.G.getName(shouldUseGermanNotation) + ", " +
                Note.A.getName(shouldUseGermanNotation) + ", " +
                Note.B.getName(shouldUseGermanNotation)
}