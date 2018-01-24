package com.pandulapeter.campfire.feature.home.settings

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.Note
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [SettingsFragment].
 */
class SettingsViewModel(
    analyticsManager: AnalyticsManager,
    private val firstTimeUserExperienceRepository: FirstTimeUserExperienceRepository,
    private val userPreferenceRepository: UserPreferenceRepository
) : HomeChildViewModel(analyticsManager) {
    val shouldShowHintsResetSnackbar = ObservableBoolean()
    val shouldAllowToolbarScrolling = ObservableBoolean()
    val shouldShowChords = ObservableBoolean(userPreferenceRepository.shouldShowChords)
    val shouldUseGermanNotation = ObservableBoolean(userPreferenceRepository.shouldUseGermanNotation)
    val englishNotationExample = ObservableField(generateNotationExample(false))
    val germanNotationExample = ObservableField(generateNotationExample(true))

    init {
        shouldShowChords.onPropertyChanged { userPreferenceRepository.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged {
            userPreferenceRepository.shouldUseGermanNotation = it
        }
    }

    fun onResetHintsClicked() {
        firstTimeUserExperienceRepository.resetAll()
        shouldShowHintsResetSnackbar.set(true)
    }

    private fun generateNotationExample(shouldUseGermanNotation: Boolean) =
        Note.C.getName(shouldUseGermanNotation) + ", " +
                Note.CSharp.getName(shouldUseGermanNotation) + ", " +
                Note.D.getName(shouldUseGermanNotation) + ", " +
                Note.DSharp.getName(shouldUseGermanNotation) + ", " +
                Note.E.getName(shouldUseGermanNotation) + ", " +
                Note.F.getName(shouldUseGermanNotation) + ", " +
                Note.FSharp.getName(shouldUseGermanNotation) + ", " +
                Note.G.getName(shouldUseGermanNotation) + ", " +
                Note.GSharp.getName(shouldUseGermanNotation) + ", " +
                Note.A.getName(shouldUseGermanNotation) + ", " +
                Note.ASharp.getName(shouldUseGermanNotation) + ", " +
                Note.B.getName(shouldUseGermanNotation)
}