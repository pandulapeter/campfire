package com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.mutableLiveDataOf

class SongAppearanceViewModel(private val preferenceDatabase: PreferenceDatabase) : CampfireViewModel() {

    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val isFirstOptionSelected = mutableLiveDataOf(preferenceDatabase.shouldShowChords && !preferenceDatabase.shouldUseGermanNotation) { onFirstOptionChanged(it) }
    val isSecondOptionSelected = mutableLiveDataOf(preferenceDatabase.shouldShowChords && preferenceDatabase.shouldUseGermanNotation) { onSecondOptionChanged(it) }
    val isThirdOptionSelected = mutableLiveDataOf(!preferenceDatabase.shouldShowChords) { onThirdOptionChanged(it) }

    private fun onFirstOptionChanged(isEnabled: Boolean) {
        if (isEnabled) {
            isSecondOptionSelected.value = false
            isThirdOptionSelected.value = false
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = false
        }
    }

    private fun onSecondOptionChanged(isEnabled: Boolean) {
        if (isEnabled) {
            isFirstOptionSelected.value = false
            isThirdOptionSelected.value = false
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = true
        }
    }

    private fun onThirdOptionChanged(isEnabled: Boolean) {
        if (isEnabled) {
            isFirstOptionSelected.value = false
            isSecondOptionSelected.value = false
            preferenceDatabase.shouldShowChords = false
        }
    }
}