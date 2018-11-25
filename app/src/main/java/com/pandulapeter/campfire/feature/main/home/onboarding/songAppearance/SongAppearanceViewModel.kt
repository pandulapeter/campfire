package com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.mutableLiveDataOf

class SongAppearanceViewModel(private val preferenceDatabase: PreferenceDatabase) : CampfireViewModel() {

    var isInitialized = false
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val isFirstOptionSelected = mutableLiveDataOf(false) { onFirstOptionChanged(it) }
    val isSecondOptionSelected = mutableLiveDataOf(false) { onSecondOptionChanged(it) }
    val isThirdOptionSelected = mutableLiveDataOf(false) { onThirdOptionChanged(it) }

    fun initialize() {
        if (!isInitialized) {
            isFirstOptionSelected.value = preferenceDatabase.shouldShowChords && !preferenceDatabase.shouldUseGermanNotation
            isSecondOptionSelected.value = preferenceDatabase.shouldShowChords && preferenceDatabase.shouldUseGermanNotation
            isThirdOptionSelected.value = !preferenceDatabase.shouldShowChords
            isInitialized = true
        }
    }

    private fun onFirstOptionChanged(isEnabled: Boolean) {
        if (isEnabled && isInitialized) {
            isSecondOptionSelected.value = false
            isThirdOptionSelected.value = false
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = false
        }
    }

    private fun onSecondOptionChanged(isEnabled: Boolean) {
        if (isEnabled && isInitialized) {
            isFirstOptionSelected.value = false
            isThirdOptionSelected.value = false
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = true
        }
    }

    private fun onThirdOptionChanged(isEnabled: Boolean) {
        if (isEnabled && isInitialized) {
            isFirstOptionSelected.value = false
            isSecondOptionSelected.value = false
            preferenceDatabase.shouldShowChords = false
        }
    }
}